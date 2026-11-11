package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;
import org.springframework.security.core.Authentication;

/** BC42 internal boundary. Context is constructed by a server workflow, never an HTTP body. */
public interface OrganizationSchoolVerificationPort {
    enum Purpose { CONFIRM_COORDINATOR,GUARD_PLACEMENT }
    final class ReadContext {
        public final Authentication actor;
        public final String termId;
        public final Purpose purpose;
        private ReadContext(Authentication actor,String termId,Purpose purpose) {this.actor=actor;this.termId=termId;this.purpose=purpose;}
        public static ReadContext fromAuthenticatedWorkflow(Authentication actor,String termId,Purpose purpose) {return new ReadContext(actor,termId,purpose);}
    }
    record SchoolVerificationView(String enterpriseOrgId,String schoolOrgId,String verificationId,String status,Long version,Instant approvedAt,boolean schoolApproved) {}
    sealed interface ReadResult permits Success,DomainError {}
    record Success(SchoolVerificationView value) implements ReadResult {}
    record DomainError(String errorCode) implements ReadResult {}
    ReadResult get(ReadContext actor,String enterpriseOrgId,String schoolOrgId);
}
