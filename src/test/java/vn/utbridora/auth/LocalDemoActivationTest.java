package vn.utbridora.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import vn.utbridora.auth.bootstrap.LocalDemoBootstrap;

class LocalDemoActivationTest {
    @Test void flagAloneDoesNotActivateSeedOutsideLocalProfile() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",java.util.Map.of("app.demo.local.enabled","true")));
            context.register(LocalDemoBootstrap.class);context.refresh();
            assertFalse(context.containsBean("seedLocalDemo"));
        }
    }
    @Test void localProfileWithoutExplicitFlagDoesNotActivateSeed() {
        try(var context=new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.register(LocalDemoBootstrap.class);context.refresh();
            assertFalse(context.containsBean("seedLocalDemo"));
        }
    }
}
