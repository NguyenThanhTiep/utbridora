package vn.utbridora.auth.onboarding;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import vn.utbridora.auth.operations.OperationFailure;

@Component
public class StudentImportWorker {
    private final StudentImportProcessor processor;private final boolean enabled;
    private final String owner=UUID.randomUUID().toString();private final AtomicBoolean busy=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("student-import-").factory());
    public StudentImportWorker(StudentImportProcessor processor,@Value("${app.onboarding.import-worker-enabled:false}") boolean enabled) {this.processor=processor;this.enabled=enabled;}
    @Scheduled(fixedDelay=1000) public void tick() {
        if(!enabled || !busy.compareAndSet(false,true)) return;
        executor.execute(()->{try {
            var lease=processor.claim(owner);if(lease==null) return;
            for(int i=0;i<20;i++) try {if(!processor.process(lease)) break;}
            catch(RuntimeException failure) {
                boolean permanent=failure instanceof OperationFailure f && f.status==422;
                processor.dependencyFailed(lease,permanent?"REFERENCE_DATA_CHANGED":"TRANSIENT_DEPENDENCY",permanent);break;
            }
        } catch(RuntimeException unavailable) { /* Durable lease/checkpoint is recovered by the next tick. No PII/SQL logging. */ }
        finally {busy.set(false);}});
    }
    @PreDestroy public void close() {executor.shutdownNow();}
}
