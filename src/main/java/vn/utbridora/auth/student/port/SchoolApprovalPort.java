
package vn.utbridora.auth.student.port;

import java.util.Optional;

public interface SchoolApprovalPort {

    Optional<ApprovalStatus> findApprovalStatus(
        String schoolOrgId,
        String studentRosterId,
        String termId
    );

    record ApprovalStatus(
        String schoolOrgId,
        String studentRosterId,
        String termId,
        boolean schoolApproved
    ) {}
}
