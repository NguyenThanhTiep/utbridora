package vn.utbridora.auth.modules.cooperation.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.modules.cooperation.persistence.VerificationTaxCheckStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Database leases survive restart and prevent duplicate completion by multiple instances. */
@Component
@EnableScheduling
@ConditionalOnProperty(name="app.cooperation.tax-verification.automatic.enabled", havingValue="true", matchIfMissing=true)
public class VerificationTaxCheckWorker implements AutoCloseable {
    private final VerificationTaxCheckStore store;
    private final TaxVerificationAssessmentService assessments;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "tax-verification"); thread.setDaemon(true); return thread;
    });
    public VerificationTaxCheckWorker(VerificationTaxCheckStore store, TaxVerificationAssessmentService assessments) {
        this.store = store; this.assessments = assessments;
    }
    @Scheduled(fixedDelayString="${app.cooperation.tax-verification.automatic.delay-ms:3000}", initialDelayString="${app.cooperation.tax-verification.automatic.initial-delay-ms:5000}")
    public void sweep() {
        if (!busy.compareAndSet(false, true)) return;
        try { executor.execute(() -> {
            try { processPending(); }
            catch (RuntimeException deferred) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Tax check deferred; durable lease will recover the attempt"); }
            finally { busy.set(false); }
        }); } catch (RejectedExecutionException shutdown) { busy.set(false); }
    }
    @Override @jakarta.annotation.PreDestroy public void close() { executor.shutdownNow(); }
    @Transactional(propagation=Propagation.NEVER)
    public void processPending() {
        // A dedicated executor isolates slow provider calls from placement and other business workers.
        for (int count = 0; count < 5; count++) {
            var claimed = store.claim();
            if (claimed.isEmpty()) return;
            var job = claimed.get(); var input = job.input();
            store.finish(job, assessments.assess(input.registrationCode(), input.registeredName(), input.address()));
        }
    }
}
