package vn.utbridora.auth.modules.matching.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.junit.jupiter.api.Assertions.*;

class SemanticConfigurationTest {
    private final ApplicationContextRunner context=new ApplicationContextRunner().withUserConfiguration(SemanticConfiguration.class);
    @Test void defaultOffNeedsNeitherEndpointTokenModelNorAiService() {
        context.run(c->{assertNull(c.getStartupFailure());assertFalse(c.getBean(SchoolSemanticObserver.class).enabled());});
    }
    @Test void disabledIgnoresUnconfiguredRuntimeValues() {
        context.withPropertyValues("app.matching.semantic.base-url=invalid","app.matching.semantic.token=missing")
            .run(c->{assertNull(c.getStartupFailure());assertFalse(c.getBean(SchoolSemanticObserver.class).enabled());});
    }
    @Test void enablingWithoutSecretFailsClosedAtConfiguration() {
        context.withPropertyValues("app.matching.semantic.enabled=true","app.matching.semantic.base-url=http://127.0.0.1:9")
            .run(c->assertNotNull(c.getStartupFailure()));
    }
    @Test void enabledCanStartWithoutConnectingToAi() {
        context.withPropertyValues("app.matching.semantic.enabled=true","app.matching.semantic.base-url=http://127.0.0.1:9",
            "app.matching.semantic.token="+SemanticFixtures.TOKEN).run(c->{
                assertNull(c.getStartupFailure());assertTrue(c.getBean(SchoolSemanticObserver.class).enabled());
            });
    }
    @Test void modelRevisionCannotDriftFromReviewedLock() {
        context.withPropertyValues("app.matching.semantic.enabled=true","app.matching.semantic.model-revision=main")
            .run(c->assertNotNull(c.getStartupFailure()));
    }
}
