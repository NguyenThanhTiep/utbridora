package vn.utbridora.auth.modules.cooperation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import vn.utbridora.auth.modules.cooperation.domain.VerificationState;

/** CP-T5UI1 canonical wire shapes. No legacy flat contact fields or legacy state names. */
public final class CooperationDtos {
    private CooperationDtos() {}
    public static final String UUID_PATTERN="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
    public record VerificationInput(
        @NotNull @Pattern(regexp=UUID_PATTERN) String schoolOrgId,
        @Pattern(regexp=UUID_PATTERN) String previousVerificationId,
        @NotBlank @Size(max=64) String registrationCode,
        @NotBlank @Size(max=200) String registeredName,
        @NotBlank @Size(max=500) String address,
        @NotBlank @Size(max=150) String contactName,
        @NotBlank @Email @Size(max=254) String contactEmail,
        @NotNull @Size(min=8,max=20) String contactPhone,
        @NotNull @Size(max=20) List<@NotNull @Pattern(regexp=UUID_PATTERN) String> evidenceFileIds) {
        public VerificationInput {if(evidenceFileIds!=null)evidenceFileIds=List.copyOf(evidenceFileIds);}
    }
    public record UpdateVerificationData(@Min(1) @Max(9007199254740991L) long expectedVersion,@NotNull @Valid VerificationInput data) {}
    public record SubmitVerification(@Min(1) @Max(9007199254740991L) long expectedVersion) {}
    public enum DecisionType { APPROVE,REJECT,REQUEST_INFO }
    public record VerificationDecision(@Min(1) @Max(9007199254740991L) long expectedVersion,@NotNull DecisionType decision,
        @Pattern(regexp="[A-Z0-9_]{3,64}") String reasonCode,
        @NotBlank @Size(max=120) String decisionRef,
        @NotNull @Size(max=20) List<@NotBlank @Size(max=64) String> missingFields) {
        public VerificationDecision {if(missingFields!=null)missingFields=List.copyOf(missingFields);}
    }
    public record VerificationView(String verificationId,String enterpriseOrgId,String schoolOrgId,VerificationState state,
        int submissionNo,long version,String previousVerificationId,VerificationInput data,Instant submittedAt,Instant decidedAt,
        String decisionRef,List<String> missingFields) {}
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages) {}
}
