package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TaxVerificationProperties.class)
public class TaxVerificationConfiguration {
    @Bean @ConditionalOnMissingBean(TaxAuthorityLookupPort.class)
    TaxAuthorityLookupPort taxAuthorityLookupPort(TaxVerificationProperties properties) {
        return properties.mode().equals("live") && properties.live().configured()
            ? new LiveTaxAuthorityAdapter(properties) : new MockTaxAuthorityAdapter();
    }
}
