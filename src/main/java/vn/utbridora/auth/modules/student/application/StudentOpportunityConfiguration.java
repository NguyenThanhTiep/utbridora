package vn.utbridora.auth.modules.student.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.utbridora.auth.modules.student.port.StudentOpportunityPort;

@Configuration
public class StudentOpportunityConfiguration {
    @Bean
    @ConditionalOnMissingBean(StudentOpportunityPort.class)
    StudentOpportunityPort unavailableStudentOpportunity(){return new UnavailableStudentOpportunity();}
}
