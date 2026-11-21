package vn.utbridora.auth.tokens;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
public class OnboardingConfiguration {
    @Bean("onboardingClock") @ConditionalOnMissingBean(name="onboardingClock")
    Clock onboardingClock() {return Clock.systemUTC();}
}
