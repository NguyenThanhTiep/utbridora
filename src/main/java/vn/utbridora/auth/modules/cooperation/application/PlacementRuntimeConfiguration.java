package vn.utbridora.auth.modules.cooperation.application;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.*;

@Configuration(proxyBeanMethods=false)
public class PlacementRuntimeConfiguration {
    @Bean Clock placementClock() {return Clock.tick(Clock.systemUTC(),Duration.ofNanos(1_000));}
}
