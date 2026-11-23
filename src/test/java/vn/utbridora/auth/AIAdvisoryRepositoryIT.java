package vn.utbridora.auth;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.matching.advisory.*;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort.Entry;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL migration/lease/admission/paging; synthetic prerequisite rows, not a business E2E. */
class AIAdvisoryRepositoryIT {
    static String id(){return UUID.randomUUID().toString();}
    @Test void migrationDurableLeaseFencingGlobalPagingRetryAndConcurrentAdmission() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora_ai_advisory").withPassword(id())) {
            mysql.start();var ds=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
            var flyway=Flyway.configure().dataSource(ds).load();flyway.migrate();assertEquals(0,flyway.migrate().migrationsExecuted);flyway.validate();
            var jdbc=new JdbcTemplate(ds);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var store=new AdvisoryRepository(jdbc);
            String school=id(),enterprise=id(),term=id(),demand=id(),dataset=id(),runId=id(),user=id();
            jdbc.update("INSERT INTO organizations(id,name,type,status,created_at) VALUES(?,'Synthetic school','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),(?,'Synthetic enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",school,enterprise);
            jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES(?,?,'SYNTHETIC-ADVISORY','2026-01-01','2030-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school);
            jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES(?,?,?,?,'DRAFT','INVALID',1,1,?,'{}',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",demand,enterprise,school,term,"a".repeat(64));
            jdbc.update("INSERT INTO student_snapshot_sets(id,school_org_id,term_id,data_json) VALUES(?,?,?,'{}')",dataset,school,term);
            var now=Instant.now();var run=new MatchingService.MatchRunRef(runId,demand,1,1,"a".repeat(64),dataset,"b".repeat(64),"strict-v1","tax-v1","preferred-v1","COMPLETED",now,now,null);
            jdbc.update("INSERT INTO match_runs(id,demand_id,school_org_id,enterprise_org_id,term_id,dataset_id,data_json) VALUES(?,?,?,?,?,?,?)",runId,demand,school,enterprise,term,dataset,JSON.writeValueAsString(new MatchingService.StoredRun(run,school,enterprise,term)));
            assertFalse(store.schoolOrigin(runId));jdbc.update("INSERT INTO matching_run_origins(run_id,audience) VALUES(?,'SCHOOL')",runId);assertTrue(store.schoolOrigin(runId));
            var stamp=new AdvisoryAuthority.Stamp(user,1,school,term,List.of(),List.of(),now.plusSeconds(86400));
            Data data=data(id(),run,stamp,school,term);tx.executeWithoutResult(s->{store.admission(runId,school);store.insert(data);});
            var first=tx.execute(s->store.claim());assertNotNull(first);assertEquals(Boolean.TRUE,tx.execute(s->store.live(first)));
            jdbc.update("UPDATE matching_advisory_jobs SET lease_until=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",data.id());
            var replacement=tx.execute(s->store.claim());assertNotNull(replacement);assertTrue(replacement.token()>first.token());assertEquals(Boolean.FALSE,tx.execute(s->store.live(first)));
            var one=new Entry(id(),id(),1,"a".repeat(64),id(),"b".repeat(64),"AVAILABLE",List.of(),List.of());var two=new Entry(id(),id(),1,"a".repeat(64),id(),"b".repeat(64),"AVAILABLE",List.of(),List.of());
            var rows=AdvisoryPolicy.rank(List.of(new AdvisoryPolicy.Unranked(one,BigDecimal.TEN,.5,false,null),new AdvisoryPolicy.Unranked(two,BigDecimal.ONE,.8,false,null)));
            var hashes=Map.of(one.studentUserId(),"c".repeat(64),two.studentUserId(),"d".repeat(64));
            tx.executeWithoutResult(s->store.publish(first,rows,new Counts(2,2,0,0),null,hashes));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM matching_advisory_rows",Integer.class));
            tx.executeWithoutResult(s->store.publish(replacement,rows,new Counts(2,2,0,0),null,hashes));assertEquals("READY",store.load(data.id(),false).state());assertEquals(1,store.items(data.id(),0,1).getFirst().position());assertEquals(2,store.items(data.id(),1,1).getFirst().position());
            try(var pool=Executors.newFixedThreadPool(2)) {
                var start=new CountDownLatch(2);Callable<String> create=()->{try {tx.executeWithoutResult(s->{jdbc.queryForObject("SELECT COUNT(*) FROM matching_advisory_jobs",Integer.class);start.countDown();try {if(!start.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Admission barrier timed out");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}store.admission(runId,school);store.insert(data(id(),run,stamp,school,term));});return "CREATED";}catch(OperationFailure blocked){return blocked.code;}};
                var a=pool.submit(create);var b=pool.submit(create);assertEquals(Set.of("CREATED","AI_ADVISORY_JOB_ACTIVE"),Set.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS)));
            }
            var retry=tx.execute(s->store.claim());assertNotNull(retry);tx.executeWithoutResult(s->store.fail(retry,"INFERENCE_UNAVAILABLE",true));assertEquals("QUEUED",store.load(retry.data().id(),false).state());
            assertTrue(jdbc.queryForObject("SELECT next_attempt_at>UTC_TIMESTAMP(6) FROM matching_advisory_jobs WHERE id=?",Boolean.class,retry.data().id()));
            jdbc.update("UPDATE matching_advisory_jobs SET expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",data.id());assertTrue(tx.execute(s->store.purgeable()).contains(data.id()));
            jdbc.update("UPDATE matching_advisory_control SET hold_enabled=true,hold_approval_ref='synthetic-approved-hold',hold_reason='Integration fixture' WHERE id=1");assertEquals(Boolean.TRUE,tx.execute(s->store.held()));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM matching_privacy_releases",Integer.class));
        }
    }
    static Data data(String id,MatchingService.MatchRunRef run,AdvisoryAuthority.Stamp stamp,String school,String term) {var now=Instant.now();return new Data(id,run,school,term,id(),id(),stamp,now,now.plusSeconds(3600),versions(run),"c".repeat(64),"d".repeat(64));}
}
