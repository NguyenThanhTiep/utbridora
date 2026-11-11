package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.organization.OrganizationRepository;

@Configuration(proxyBeanMethods=false)
public class DemandAcademicConfiguration {
    @Bean @ConditionalOnMissingBean(DemandAcademicPort.class)
    DemandAcademicPort termAcademicReferences(TermRepository terms,OrganizationRepository organizations,ObjectProvider<DemandMajorCatalogPort> majors) {
        return new TermAcademicReferences(terms,organizations,majors);
    }
}
