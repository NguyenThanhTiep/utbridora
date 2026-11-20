package vn.utbridora.auth;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.support.ClamdFixture;
import static org.junit.jupiter.api.Assertions.*;

/** Package9 operational tools against isolated real MySQL/ClamD/private files. Never a shared daemon locally. */
class Package9OperationsIT {
    static final JsonMapper JSON=JsonMapper.builder().build();
    static String uid(){return UUID.randomUUID().toString();}
    static String schema(){return "utbridora_pkg9_"+uid().replace("-","").substring(0,16);}
    @Test void dumpAndPrivateFileRecoveryThenDay31And91RetirementWithHoldReplayAndStaleGuards() throws Exception {
        String source=schema(),clone=schema(),user=uid(),org=uid(),file=uid(),scope=uid(),audit=uid();
        Path root=Files.createTempDirectory("utbridora-pkg9-it-");
        try(var mysql=new MySQLContainer<>("mysql:8.4").withDatabaseName(source).withUsername("root").withPassword(uid());
            var clamd=ClamdFixture.start()) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load().migrate();
            String server;
            try(var c=connection(mysql,source);var s=c.createStatement()){
                try(var rows=s.executeQuery("SELECT @@server_uuid")){assertTrue(rows.next());server=rows.getString(1);}
                s.execute("CREATE DATABASE `"+clone+"`");
                s.execute("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES('"+user+"','synthetic@demo.invalid','!NO_LOGIN!','Synthetic student','ACTIVE','2026-01-01','2026-01-01')");
                s.execute("INSERT INTO organizations(id,name,type,status,created_at) VALUES('"+org+"','Synthetic school','SCHOOL','ACTIVE','2026-01-01')");
                s.execute("INSERT INTO memberships(id,user_id,organization_id,role,created_at) VALUES('"+uid()+"','"+user+"','"+org+"','STUDENT','2026-01-01')");
                s.execute("INSERT INTO audit_logs(id,actor_id,action,resource_type,resource_id,result,created_at) VALUES('"+audit+"','"+user+"','FIXTURE','synthetic','"+file+"','SUCCESS','2026-01-01')");
                // Nonempty cyclic FK prerequisites, not evidence of a business workflow.
                String term=uid(),demand=uid(),cooperation=uid(),claim=uid(),token=uid(),mentor=uid();
                s.execute("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES('"+term+"','"+org+"','SYNTHETIC-RETIRE','2026-01-01','2026-06-01','ACTIVE',1,'2026-01-01','2026-01-01')");
                s.execute("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES('"+demand+"','"+org+"','"+org+"','"+term+"','DRAFT','INVALID',1,1,'"+"a".repeat(64)+"','{}','2026-01-01','2026-01-01')");
                s.execute("INSERT INTO cooperations(id,demand_id,school_org_id,enterprise_org_id,term_id,state,created_at) VALUES('"+cooperation+"','"+demand+"','"+org+"','"+org+"','"+term+"','DRAFT','2026-01-01')");
                s.execute("UPDATE demands SET cooperation_id='"+cooperation+"' WHERE id='"+demand+"'");
                s.execute("INSERT INTO invitation_claims(id,email_canonical,context_kind,claim_state,generation,created_at,updated_at) VALUES('"+claim+"','mentor@synthetic.invalid','MENTOR','PENDING',1,'2026-01-01','2026-01-01')");
                s.execute("INSERT INTO one_time_tokens(id,token_hash,purpose,user_id,email,expires_at,created_at,issued_at,claim_id,generation) VALUES('"+token+"','"+"b".repeat(64)+"','INVITATION','"+user+"','mentor@synthetic.invalid','2026-01-03','2026-01-01','2026-01-01','"+claim+"',1)");
                s.execute("INSERT INTO mentor_invitation_refs(id,email,user_id,token_id,hr_user_id,state,scope_json,assignment_ids,expires_at) VALUES('"+mentor+"','mentor@synthetic.invalid','"+user+"','"+token+"','"+user+"','ACCOUNT_PENDING','{}','[]','2026-01-03')");
                s.execute("UPDATE invitation_claims SET current_token_id='"+token+"',invitation_ref_id='"+mentor+"' WHERE id='"+claim+"'");
                String kase=uid(),cohort=uid(),group=uid(),task=uid(),assignment=uid(),submission=uid(),template=uid(),evaluation=uid(),delegation=uid(),coordinator=uid(),hr=uid();
                s.execute("INSERT INTO internship_cases VALUES('"+kase+"','"+uid()+"','"+user+"','"+org+"','"+org+"','"+term+"','IN_PROGRESS',1,1)");
                s.execute("INSERT INTO internship_cohorts VALUES('"+cohort+"','"+org+"','"+org+"','"+term+"','RETIRE','Synthetic retirement',1)");
                s.execute("INSERT INTO internship_groups VALUES('"+group+"','"+cohort+"','G1',1)");
                s.execute("INSERT INTO internship_tasks VALUES('"+task+"','"+group+"','Synthetic','Synthetic','2026-06-01','NORMAL',false,1)");
                s.execute("INSERT INTO internship_task_assignments(id,task_id,case_id,student_user_id,state,version) VALUES('"+assignment+"','"+task+"','"+kase+"','"+user+"','SUBMITTED',1)");
                s.execute("INSERT INTO internship_submissions(id,assignment_id,version_no,submitted_at,late_flag) VALUES('"+submission+"','"+assignment+"',1,'2026-01-01',false)");
                s.execute("UPDATE internship_task_assignments SET current_submission_id='"+submission+"' WHERE id='"+assignment+"'");
                s.execute("INSERT INTO internship_rubric_templates VALUES('"+template+"',1,'"+org+"','"+term+"','{}')");
                s.execute("INSERT INTO internship_evaluations(id,case_id) VALUES('"+evaluation+"','"+kase+"')");
                s.execute("INSERT INTO internship_evaluation_versions VALUES('"+evaluation+"',1,1,'PUBLISHED','"+template+"',1,'{}',80,'"+user+"','2026-01-01')");
                s.execute("UPDATE internship_evaluations SET current_version=1,published_version=1 WHERE id='"+evaluation+"'");
                s.execute("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active) VALUES('"+delegation+"','"+user+"','"+org+"','"+term+"','"+user+"','SYNTHETIC','[]','2026-01-01','2026-06-01',true)");
                s.execute("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,school_delegation_id,granted_by,decision_ref,valid_from,valid_until) VALUES('"+coordinator+"','COORDINATOR','"+user+"','"+org+"','"+org+"','"+term+"','"+delegation+"','"+user+"','SYNTHETIC','2026-01-01','2026-06-01')");
                s.execute("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,parent_assignment_id,granted_by,decision_ref,valid_from,valid_until) VALUES('"+hr+"','HR','"+user+"','"+org+"','"+org+"','"+term+"','"+coordinator+"','"+user+"','SYNTHETIC','2026-01-01','2026-06-01')");
            }
            var cfg=config(root.resolve("source"),source,scope,server,mysql);
            Path storage=Path.of(((Map<?,?>)((List<?>)cfg.get("resources")).getFirst()).get("path").toString());
            byte[] pdf="%PDF-1.7\nSynthetic package9 recovery\n%%EOF".getBytes(StandardCharsets.UTF_8);
            var adapter=new PrivateFileStorage(storage.toString());String key,sha;
            try(var staged=adapter.stage(new ByteArrayInputStream(pdf),1024);var scanner=new ClamdFileScanner(clamd.getHost(),clamd.getMappedPort(3310))){
                assertEquals(FileScanner.Outcome.CLEAN,scanner.scan(staged.open(),pdf.length,Duration.ofSeconds(60)).outcome());
                sha=staged.sha256();key=adapter.promote(staged);
            }
            try(var c=connection(mysql,source);var s=c.createStatement()){
                s.execute("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES('"+file+"','"+user+"','"+org+"','"+key+"','cv.pdf','application/pdf',"+pdf.length+",'"+sha+"','PRIVATE','CLEAN','2026-01-01')");
                s.execute("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES('"+uid()+"','"+file+"','STUDENT_PROFILE','CV','"+user+"','2026-01-01')");
            }
            Path cfgPath=root.resolve("source.json");write(cfgPath,cfg);
            Path backups=Path.of(((Map<?,?>)((List<?>)cfg.get("resources")).get(1)).get("path").toString());
            exec(mysql,"export MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\"; exec mysqldump -uroot --single-transaction --hex-blob --no-tablespaces --set-gtid-purged=OFF --result-file=/tmp/pkg9.sql "+source);
            Path dump=backups.resolve("original.sql");mysql.copyFileFromContainer("/tmp/pkg9.sql",dump.toString());
            Path bundle=backups.resolve("paired-backup");
            run(mysql,true,cfgPath,root.resolve("backup-result.json"),"backup","--dump",dump.toString(),"--bundle",bundle.toString());
            mysql.copyFileToContainer(MountableFile.forHostPath(bundle.resolve("database.sql")),"/tmp/pkg9-restore.sql");
            exec(mysql,"export MYSQL_PWD=\"$MYSQL_ROOT_PASSWORD\"; exec mysql -uroot "+clone+" < /tmp/pkg9-restore.sql");
            var cloneCfg=config(root.resolve("clone"),clone,uid(),server,mysql);Path clonePath=root.resolve("clone.json");write(clonePath,cloneCfg);
            Path restored=root.resolve("restore-result.json");
            run(mysql,true,clonePath,restored,"restore-files","--bundle",bundle.toString());
            Path cloneStorage=Path.of(((Map<?,?>)((List<?>)cloneCfg.get("resources")).getFirst()).get("path").toString());
            try(var channel=new PrivateFileStorage(cloneStorage.toString()).open(key,pdf.length,sha)){
                assertArrayEquals(pdf,java.nio.channels.Channels.newInputStream(channel).readAllBytes());
            }
            var cloneFlyway=Flyway.configure().dataSource(url(mysql,clone),mysql.getUsername(),mysql.getPassword()).load();
            cloneFlyway.validate();assertEquals(0,cloneFlyway.migrate().migrationsExecuted);
            run(mysql,false,clonePath,root.resolve("not-empty.json"),"restore-files","--bundle",bundle.toString());
            // A real tampered blob must fail before attaching any bytes to a fresh restored root.
            Path blob=bundle.resolve("files").resolve(key);byte[] original=Files.readAllBytes(blob);Files.writeString(blob,"tampered");
            run(mysql,false,clonePath,root.resolve("tampered.json"),"restore-files","--bundle",bundle.toString());Files.write(blob,original);
            Path early=root.resolve("early-plan.json");
            run(mysql,true,cfgPath,early,"plan-retention","--now","2026-01-30T00:00:00Z");
            run(mysql,false,cfgPath,root.resolve("early-apply.json"),"apply-retention","--plan",early.toString(),"--now","2026-01-30T00:00:00Z","--confirm-scope",scope);
            var held=new LinkedHashMap<>(cfg);held.put("hold",Map.of("approvalRef","SYNTHETIC-HOLD","reason","Recovery investigation"));
            Path holdPath=root.resolve("hold.json");write(holdPath,held);Path holdPlan=root.resolve("hold-plan.json");
            run(mysql,true,holdPath,holdPlan,"plan-retention","--now","2026-02-01T00:00:00Z");
            run(mysql,false,holdPath,root.resolve("held-apply.json"),"apply-retention","--plan",holdPlan.toString(),"--now","2026-02-01T00:00:00Z","--confirm-scope",scope);
            Path stale=root.resolve("stale-plan.json");run(mysql,true,cfgPath,stale,"plan-retention","--now","2026-02-01T00:00:00Z");
            Path export=Path.of(((Map<?,?>)((List<?>)cfg.get("resources")).get(2)).get("path").toString()).resolve("new-export.csv");Files.writeString(export,"synthetic-new-copy");
            run(mysql,false,cfgPath,root.resolve("stale-apply.json"),"apply-retention","--plan",stale.toString(),"--now","2026-02-01T00:00:00Z","--confirm-scope",scope);
            assertEquals(1,count(mysql,source,"files"));assertTrue(Files.exists(storage.resolve(key)));
            Path outside=root.resolve("outside.keep");Files.writeString(outside,"unrelated data");
            Path plan=root.resolve("day31-plan.json"),receipt=root.resolve("day31-result.json");
            run(mysql,true,cfgPath,plan,"plan-retention","--now","2026-02-01T00:00:00Z");
            // Two real processes compete for the same maintenance scope. At least one applies;
            // the other either refuses the lock or reads the committed idempotent receipt.
            Path secondReceipt=root.resolve("day31-competing-result.json");
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
                var futures=pool.invokeAll(List.<java.util.concurrent.Callable<Integer>>of(
                    ()->outcome(mysql,cfgPath,receipt,"apply-retention","--plan",plan.toString(),"--now","2026-02-01T00:00:00Z","--confirm-scope",scope),
                    ()->outcome(mysql,cfgPath,secondReceipt,"apply-retention","--plan",plan.toString(),"--now","2026-02-01T00:00:00Z","--confirm-scope",scope)));
                assertTrue(futures.stream().anyMatch(f->{try{return f.get()==0;}catch(Exception e){throw new RuntimeException(e);}}),"At least one concurrent retirement must commit");
            }
            if(!Files.exists(receipt))Files.copy(secondReceipt,receipt);
            run(mysql,true,cfgPath,root.resolve("replay-result.json"),"apply-retention","--plan",plan.toString(),"--now","2026-02-01T00:00:00Z","--confirm-scope",scope);
            assertEquals(0,count(mysql,source,"files"));assertEquals(0,count(mysql,source,"file_bindings"));assertEquals(0,count(mysql,source,"memberships"));
            for(String table:List.of("demands","cooperations","one_time_tokens","invitation_claims","mentor_invitation_refs","internship_context_assignments","internship_submissions","internship_task_assignments","internship_evaluations","internship_evaluation_versions"))assertEquals(0,count(mysql,source,table));
            assertEquals(2,count(mysql,source,"audit_logs"));assertFalse(Files.exists(storage.resolve(key)));assertFalse(Files.exists(dump));assertFalse(Files.exists(export));assertTrue(Files.exists(outside));
            assertEquals(1,count(mysql,clone,"files"));assertArrayEquals(pdf,Files.readAllBytes(cloneStorage.resolve(key)));
            try(var c=connection(mysql,source);var s=c.createStatement();var r=s.executeQuery("SELECT email,password_hash,status FROM users WHERE id='"+user+"'")){
                assertTrue(r.next());assertEquals("retired-"+user+"@invalid",r.getString(1));assertEquals("!RETIRED_NO_LOGIN!",r.getString(2));assertEquals("DEACTIVATED",r.getString(3));
            }
            Path day91=root.resolve("day91-plan.json");run(mysql,true,cfgPath,day91,"plan-retention","--now","2026-04-02T00:00:00Z");
            run(mysql,true,cfgPath,root.resolve("day91-result.json"),"apply-retention","--plan",day91.toString(),"--now","2026-04-02T00:00:00Z","--confirm-scope",scope);
            try(var c=connection(mysql,source);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM audit_logs WHERE id='"+audit+"'")){assertTrue(r.next());assertEquals(0,r.getInt(1));}
            // Retirement preserves Flyway history/checksums and leaves every FK enabled.
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load().validate();
            Files.createDirectories(Path.of("target/package9"));
            var evidence=new LinkedHashMap<String,Object>();evidence.put("status","PASS");evidence.put("commit",System.getenv().getOrDefault("GITHUB_SHA","LOCAL_UNVERIFIED"));
            evidence.put("restore",JSON.readTree(Files.readString(restored)));evidence.put("day31",JSON.readTree(Files.readString(receipt)));
            evidence.put("gates",List.of("real ClamD scan","real SQL dump/restore","private bytes/hash/open","Flyway validate/no-op","day31 bindings/PII/registered-copies removed","audit retained until90d","day91 audit expiry","hold","stale plan","concurrent apply","replay","tampered blob","nonempty restore","unrelated clone preserved"));
            evidence.put("limits",List.of("Ephemeral synthetic rehearsal; no staging restore/RPO/RTO certification","Retirement requires a separate exclusive offline scope; mixed shared schema is refused"));
            write(Path.of("target/package9/operations-verification.json"),evidence);
        } finally {
            // Only this test-created path. No shared/private developer storage is touched.
            try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
    static Map<String,Object> config(Path base,String schema,String scope,String server,MySQLContainer<?> mysql) throws Exception {
        Files.createDirectories(base);var resources=new ArrayList<Map<String,String>>();
        for(String kind:List.of("private-files","backup","export")){
            Path dir=Files.createDirectory(base.resolve(kind));write(dir.resolve(".utbridora-demo-scope.json"),Map.of("schema",schema,"scopeId",scope));
            resources.add(Map.of("kind",kind,"path",dir.toAbsolutePath().toString()));
        }
        var c=new LinkedHashMap<String,Object>();c.put("version",1);c.put("exclusiveSynthetic",true);c.put("writersStopped",true);c.put("allCopiesRegistered",true);
        c.put("schema",schema);c.put("scopeId",scope);c.put("serverUuid",server);c.put("host","127.0.0.1");c.put("port",mysql.getMappedPort(3306));c.put("user",mysql.getUsername());
        c.put("acceptedAt","2026-01-01T00:00:00Z");c.put("approvalRef","SYNTHETIC-TEST-NOT-USER-ACCEPTANCE");c.put("resources",resources);return c;
    }
    static void write(Path path,Object data) throws Exception {Files.writeString(path,JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));}
    static void run(MySQLContainer<?> mysql,boolean success,Path cfg,Path output,String...args) throws Exception {
        int code=outcome(mysql,cfg,output,args);
        if(success)assertEquals(0,code,"Operational command must succeed");else assertNotEquals(0,code,"Operation should have refused");
    }
    static int outcome(MySQLContainer<?> mysql,Path cfg,Path output,String...args) throws Exception {
        var command=new ArrayList<>(List.of(System.getenv().getOrDefault("PYTHON","python3"),"scripts/ops/handoff.py","--config",cfg.toString(),"--output",output.toString()));command.addAll(List.of(args));
        var builder=new ProcessBuilder(command).redirectErrorStream(true);builder.environment().put("UTB_OPS_DB_PASSWORD",mysql.getPassword());
        var process=builder.start();String log=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);int code=process.waitFor();
        if(code!=0)System.out.println(log);return code;
    }
    static long count(MySQLContainer<?> mysql,String schema,String table) throws Exception {try(var c=connection(mysql,schema);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM `"+table+"`")){assertTrue(r.next());return r.getLong(1);}}
    static String url(MySQLContainer<?> mysql,String schema){return mysql.getJdbcUrl().replace("/"+mysql.getDatabaseName(),"/"+schema);}
    static Connection connection(MySQLContainer<?> mysql,String schema) throws Exception{return DriverManager.getConnection(url(mysql,schema),mysql.getUsername(),mysql.getPassword());}
    static void exec(MySQLContainer<?> mysql,String cmd) throws Exception {var r=mysql.execInContainer("sh","-c",cmd);assertEquals(0,r.getExitCode(),"Isolated MySQL operation failed");}
}
