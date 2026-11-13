package vn.utbridora.auth.modules.student.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.utbridora.auth.modules.student.port.StudentOpportunityPort;
import vn.utbridora.auth.modules.cooperation.port.OpportunityPort;
import vn.utbridora.auth.contracts.ActorContext;
import java.util.Optional;

@Configuration
public class StudentOpportunityConfiguration {
    @Bean
    @ConditionalOnMissingBean(StudentOpportunityPort.class)
    StudentOpportunityPort studentOpportunity(OpportunityPort provider) {
        return (student, id) -> {
            var ref = provider.readForPreference(ActorContext.fromAuthenticatedWorkflow(student, null), id);
            return Optional.of(new StudentOpportunityPort.OpportunityRef(ref.opportunityId(), ref.schoolOrgId(),
                ref.enterpriseOrgId(), ref.termId(), ref.publishedAt(), ref.closedAt(), ref.version()));
        };
    }
}
