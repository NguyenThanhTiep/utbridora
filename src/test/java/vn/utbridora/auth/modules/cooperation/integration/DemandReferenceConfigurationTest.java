package vn.utbridora.auth.modules.cooperation.integration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;
import static org.junit.jupiter.api.Assertions.*;

class DemandReferenceConfigurationTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner()
        .withUserConfiguration(DefaultDemandReferenceAdapter.class,DemandReferenceConfiguration.class);
    @Test void defaultConfigurationDeniesInsteadOfUsingDemoCatalog() {
        context.run(c->{
            assertNull(c.getStartupFailure());
            var port=c.getBean(DemandReferencePort.class);
            assertFalse(port instanceof DefaultDemandReferenceAdapter);
            assertEquals("DEPENDENCY_UNAVAILABLE",assertThrows(DemandFailure.class,()->port.validate("org",null)).code());
        });
    }
    @Test void explicitDemoFlagSelectsOnlyOneAdapter() {
        context.withPropertyValues("app.t5.demand.default-reference-adapter.enabled=true").run(c->{
            assertNull(c.getStartupFailure());
            assertEquals(1,c.getBeansOfType(DemandReferencePort.class).size());
            assertInstanceOf(DefaultDemandReferenceAdapter.class,c.getBean(DemandReferencePort.class));
        });
    }
    @Test void suppliedAuthoritativeAdapterReplacesUnavailableFallback() {
        DemandReferencePort supplied=(org,input)->{};
        context.withBean("authoritativeReferences",DemandReferencePort.class,()->supplied).run(c->{
            assertNull(c.getStartupFailure()); assertSame(supplied,c.getBean(DemandReferencePort.class));
        });
    }
}
