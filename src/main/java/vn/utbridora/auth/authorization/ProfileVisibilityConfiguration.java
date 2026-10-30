package vn.utbridora.auth.authorization;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProfileVisibilityConfiguration {
    @Bean
    @ConditionalOnMissingBean(ProfileVisibilityPort.class)
    ProfileVisibilityPort denyAllProfileVisibility(){return new DenyAllProfileVisibility();}
}
