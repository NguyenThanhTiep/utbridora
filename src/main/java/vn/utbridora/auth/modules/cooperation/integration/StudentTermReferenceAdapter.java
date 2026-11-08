package vn.utbridora.auth.modules.cooperation.integration;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.student.port.TermReferencePort;

/** T5-owned adapter for T2's existing consumer boundary; not a browser-trusted context or public callback. */
@Service
public class StudentTermReferenceAdapter implements TermReferencePort {
    private final TermRepository terms;
    public StudentTermReferenceAdapter(TermRepository terms) { this.terms=terms; }
    @Override @Transactional(readOnly=true)
    public Optional<TermReference> findById(String id) {
        return terms.findById(id).map(term->new TermReference(term.id,term.termCode,term.schoolOrgId,
                term.startDate,term.endDate,Status.valueOf(term.status.name()),term.version));
    }
}
