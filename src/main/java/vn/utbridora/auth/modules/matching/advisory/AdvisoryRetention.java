package vn.utbridora.auth.modules.matching.advisory;

import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort;

/** Source TTL cleanup continues when inference/view flags are OFF. No AI client or executor. */
@Component
public class AdvisoryRetention {
    private final AdvisoryRepository store;private final StudentSemanticSnapshotPort students;private final DemandSemanticSnapshotPort demands;private final AuditService audit;private final TransactionTemplate tx;
    public AdvisoryRetention(AdvisoryRepository store,StudentSemanticSnapshotPort students,DemandSemanticSnapshotPort demands,AuditService audit,PlatformTransactionManager manager) {
        this.store=store;this.students=students;this.demands=demands;this.audit=audit;this.tx=new TransactionTemplate(manager);this.tx.setTimeout(5);
    }
    @Scheduled(fixedDelay=10000,initialDelay=10000) public void tick() {
        try {
            tx.executeWithoutResult(s->{for(String id:store.expired()) {var job=store.load(id,true);store.invalidate(job,"EXPIRED","TTL_EXPIRED");audit.workflow(null,"AI_ADVISORY_INVALIDATED",id,job.data().school(),Map.of("matchRunId",job.data().run().matchRunId(),"state","EXPIRED","reasonCode","TTL_EXPIRED"));}});
            tx.executeWithoutResult(s->{if(store.held())return;for(String id:store.purgeable()){var job=store.load(id,true);students.purge(job.data().studentSet());demands.purge(job.data().demandSnapshot());store.purged(id);}});
        } catch(RuntimeException unavailable) {org.slf4j.LoggerFactory.getLogger(AdvisoryRetention.class).warn("Advisory retention deferred; will retry");}
    }
}
