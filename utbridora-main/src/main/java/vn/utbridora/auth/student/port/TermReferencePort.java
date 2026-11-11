
package vn.utbridora.auth.student.port;

import java.time.LocalDate;
import java.util.Optional;

public interface TermReferencePort {

    Optional<TermReference> findById(String termId);

    record TermReference(
        String termId,
        String termCode,
        String schoolOrgId,
        LocalDate startDate,
        LocalDate endDate,
        Status status,
        long version
    ) {}

    enum Status {
        DRAFT,
        ACTIVE,
        CLOSED
    }
}
