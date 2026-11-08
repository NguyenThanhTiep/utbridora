package vn.utbridora.auth.modules.student.application;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class ProfileReferenceConfiguration {
    @Bean @ConditionalOnMissingBean(ProfileReferenceValidator.class)
    ProfileReferenceValidator unavailableProfileReferences() {
        return (school,input)->{
            // Null major and explicitly empty selections need no catalog lookup (BC11).
            if(input.majorCode()!=null || !input.skills().isEmpty() || !input.customSkills().isEmpty()
                    || !input.orientationCodes().isEmpty() || !input.subjectCodes().isEmpty()
                    || input.projects().stream().anyMatch(project->!project.skillCodes().isEmpty())) {
                throw StudentProfileException.of(503,"DEPENDENCY_UNAVAILABLE","Danh mục học vụ hoặc kỹ năng chưa sẵn sàng để xác minh lựa chọn.");
            }
        };
    }
}
