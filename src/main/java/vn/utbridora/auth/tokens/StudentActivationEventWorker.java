package vn.utbridora.auth.tokens;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StudentActivationEventWorker {
    private final StudentActivationEvents events;private final boolean enabled;
    public StudentActivationEventWorker(StudentActivationEvents events,@Value("${app.onboarding.activation-events-enabled:false}") boolean enabled) {this.events=events;this.enabled=enabled;}
    @Scheduled(fixedDelay=1000) public void tick() {
        if(!enabled) return;
        try {for(int i=0;i<20 && events.dispatchOne();i++) { }} catch(RuntimeException unavailable) { /* Retry durable PENDING records; no payload logs. */ }
    }
}
