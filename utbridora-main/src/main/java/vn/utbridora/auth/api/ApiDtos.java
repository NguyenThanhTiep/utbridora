package vn.utbridora.auth.api;

import jakarta.validation.constraints.*;
import java.util.List;
import vn.utbridora.auth.user.*;
import vn.utbridora.auth.organization.*;

public final class ApiDtos {
    private ApiDtos() {}
    public record Login(@NotBlank @Email @Size(max=254) String email,@NotBlank @Size(max=1000) String password) {}
    public record ChangePassword(@NotBlank @Size(max=1000) String currentPassword,@NotBlank @Size(max=1000) String newPassword) {}
    public record ForgotPassword(@NotBlank @Email @Size(max=254) String email) {}
    public record ResetPassword(@NotBlank @Size(max=128) String token,@NotBlank @Size(max=1000) String newPassword) {}
    public record AcceptInvitation(@NotBlank @Size(max=128) String token,@NotBlank @Size(max=150) String displayName,@NotBlank @Size(max=1000) String password) {}
    public record CreateOrganization(@NotBlank @Size(max=200) String name,@NotNull Organization.Type type) {}
    public record CreateInvitation(@NotBlank @Email @Size(max=254) String email,@NotNull Role role,@Size(max=36) String organizationId) {}
    public record UpdateStatus(@NotNull AccountStatus status,
         @NotBlank @Pattern(regexp="[A-Z0-9_]{3,64}") String reasonCode) {}
    public record ChangeMembership(@NotBlank @Pattern(regexp="[0-9a-fA-F-]{36}") String userId,@NotNull Role role,@Size(max=36) String organizationId) {}
    public record MembershipView(Role role,String organizationId) {}
    public record Me(String userId,String email,String displayName,List<MembershipView> memberships) {}
    public record Message(String message) {}
}
