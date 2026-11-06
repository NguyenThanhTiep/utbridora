package vn.utbridora.auth.security;

import java.util.Objects;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("profileGuard")
public class ProfileGuard {

    public boolean isOwner(String targetUserId, Authentication authentication) {
        if (authentication == null || targetUserId == null) return false;
        return Objects.equals(authentication.getName(), targetUserId);
    }

    public boolean isOwnerOrStaff(String targetUserId, Authentication authentication) {
        if (isOwner(targetUserId, authentication)) return true;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("student_profile:school:read") 
                            || a.getAuthority().equals("ROLE_ADMIN"));
    }
}