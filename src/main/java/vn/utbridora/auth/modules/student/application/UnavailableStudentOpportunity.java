package vn.utbridora.auth.modules.student.application;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.student.port.StudentOpportunityPort;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Fail-closed default until T5 provides the Opportunity adapter: no opportunity is assumed, submit answers 503. */
public final class UnavailableStudentOpportunity implements StudentOpportunityPort {
    @Override public Optional<OpportunityRef> publishedForStudent(Authentication student,String opportunityId) {
        throw failure(503,"DEPENDENCY_UNAVAILABLE");
    }
}
