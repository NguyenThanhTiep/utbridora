package vn.utbridora.auth.modules.student.application;

import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.student.StudentTermLinkService;
import vn.utbridora.auth.student.port.TermReferencePort;

/** Composition of the real T5 reference provider and the T2 relationship owner; no approval is fabricated. */
@Component
public class LinkedStudentTermScope implements StudentTermScopePort {
    private final TermReferencePort terms;
    private final StudentTermLinkService links;
    public LinkedStudentTermScope(TermReferencePort terms,StudentTermLinkService links) { this.terms=terms;this.links=links; }
    @Override @Transactional(readOnly=true)
    public Optional<String> schoolOfLinkedTerm(String userId,String termId) {
        return terms.findById(termId).filter(term->term.status()==TermReferencePort.Status.ACTIVE)
                .filter(term->links.linked(userId,term.schoolOrgId(),term.termId())).map(TermReferencePort.TermReference::schoolOrgId);
    }
}
