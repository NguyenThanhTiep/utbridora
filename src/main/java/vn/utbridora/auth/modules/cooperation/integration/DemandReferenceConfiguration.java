package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.utbridora.auth.modules.cooperation.domain.DemandFailure;

@Configuration(proxyBeanMethods=false)
public class DemandReferenceConfiguration {
    @Bean
    @ConditionalOnMissingBean(DemandReferencePort.class)
    DemandReferencePort unavailableDemandReferences() {
        return (org,input) -> {
            throw new DemandFailure(503, "DEPENDENCY_UNAVAILABLE", "Chưa tích hợp danh mục kỹ năng.");
        };
    }
}
