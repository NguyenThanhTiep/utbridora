package vn.utbridora.auth.tokens;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ActivationMailWorker {
    private final ActivationMailQueue queue;private final ActivationMailCipher cipher;private final EmailService email;private final boolean enabled;
    private final ExecutorService executor=Executors.newFixedThreadPool(2,Thread.ofPlatform().daemon().name("activation-mail-",0).factory());
    private final AtomicInteger busy=new AtomicInteger();
    public ActivationMailWorker(ActivationMailQueue queue,ActivationMailCipher cipher,EmailService email,@Value("${app.onboarding.mail.worker-enabled:false}") boolean enabled) {this.queue=queue;this.cipher=cipher;this.email=email;this.enabled=enabled;}
    @Scheduled(fixedDelay=1000) public void tick() {
        if(!enabled) return;
        for(int i=0;i<2;i++) {
            if(busy.incrementAndGet()>2) {busy.decrementAndGet();break;}
            executor.execute(()->{try {for(int n=0;n<10;n++) {
                var d=queue.claim(UUID.randomUUID().toString());if(d==null) break;
                String error=null;boolean permanent=false;
                try {String token=cipher.decrypt(d.payload(),d.claimId(),d.generation(),d.id());email.activation(d.email(),token,d.messageId());}
                catch(vn.utbridora.auth.operations.OperationFailure key) {error="TRANSPORT_KEY_UNAVAILABLE";}
                catch(org.springframework.mail.MailAuthenticationException | org.springframework.mail.MailParseException rejected) {error="SMTP_PERMANENT";permanent=true;}
                catch(RuntimeException unavailable) {error="SMTP_TEMPORARY";}
                queue.complete(d,error,permanent);
            }} catch(RuntimeException unavailable) { /* Durable leases recover without logging payloads. */ }
            finally {busy.decrementAndGet();}});
        }
    }
    @PreDestroy public void close() {executor.shutdownNow();}
}
