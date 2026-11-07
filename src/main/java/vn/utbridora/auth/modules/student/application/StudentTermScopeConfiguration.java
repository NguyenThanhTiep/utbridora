package vn.utbridora.auth.modules.student.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudentTermScopeConfiguration {
    @Bean
    @ConditionalOnMissingBean(StudentTermScopePort.class)
    StudentTermScopePort unavailableStudentTermScope(){return new UnavailableStudentTermScope();}
}
