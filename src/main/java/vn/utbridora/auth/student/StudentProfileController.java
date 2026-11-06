package vn.utbridora.auth.student;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/students")
public class StudentProfileController {

    private final StudentProfileService profileService;

    public StudentProfileController(StudentProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me/profile")
    @PreAuthorize("hasAuthority('student_profile:self:read') or hasRole('STUDENT')")
    public ResponseEntity<StudentProfileResponse> getMyProfile(Authentication authentication) {
        String currentUserId = authentication.getName();
        return ResponseEntity.ok(profileService.getProfileByUserId(currentUserId));
    }

    @PutMapping("/me/profile")
    @PreAuthorize("hasAuthority('student_profile:self:write') or hasRole('STUDENT')")
    public ResponseEntity<StudentProfileResponse> updateMyProfile(
            Authentication authentication,
            @Valid @RequestBody StudentProfileRequest request) {
        String currentUserId = authentication.getName();
        return ResponseEntity.ok(profileService.createOrUpdateProfile(currentUserId, request));
    }

    @GetMapping("/{userId}/profile")
    @PreAuthorize("@profileGuard.isOwnerOrStaff(#userId, authentication)")
    public ResponseEntity<StudentProfileResponse> getProfileByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(profileService.getProfileByUserId(userId));
    }
}