package vn.utbridora.auth.modules.cooperation.application;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.Term;
import vn.utbridora.auth.modules.cooperation.domain.TermStatus;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.organization.Organization;
import vn.utbridora.auth.organization.OrganizationRepository;

/**
 * Business logic for TermReference.
 * Owner: T5. School-Staff creates/manages terms for their own university.
 */
@Service
public class TermService {

    private final TermRepository terms;
    private final OrganizationRepository organizations;
    private final AuditService audit;

    public TermService(TermRepository terms, OrganizationRepository organizations, AuditService audit) {
        this.terms = terms;
        this.organizations = organizations;
        this.audit = audit;
    }

    // ── Reads ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<TermView> listBySchool(String schoolOrgId, TermStatus statusFilter,
                                       int page, int size) {
        validatePagination(page, size);
        PageRequest paging = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by("id")));
        Page<Term> result = statusFilter != null
                ? terms.findBySchoolOrgIdAndStatus(schoolOrgId, statusFilter, paging)
                : terms.findBySchoolOrgId(schoolOrgId, paging);
        return result.map(this::toView);
    }

    @Transactional(readOnly = true)
    public TermView getById(String termId) {
        Term t = terms.findById(termId)
                .orElseThrow(() -> new IllegalArgumentException("Term not found"));
        return toView(t);
    }

    // ── Mutations ──────────────────────────────────────────────────────────

    /**
     * School-staff creates a new term for their school organization.
     * The schoolOrgId comes from the authenticated actor's verified membership, not the request body.
     */
    @Transactional
    public TermView create(String actorId, String schoolOrgId, CreateTerm body) {
        Organization school = organizations.findById(schoolOrgId)
                .orElseThrow(() -> new IllegalArgumentException("School organization not found"));
        if (school.type != Organization.Type.UNIVERSITY)
            throw new IllegalArgumentException("Terms can only be created for university organizations");
        if (school.status != Organization.Status.VERIFIED)
            throw new IllegalArgumentException("School organization must be VERIFIED to create terms");
        if (!body.startDate().isBefore(body.endDate()))
            throw new IllegalArgumentException("startDate must be before endDate");
        if (terms.existsBySchoolOrgIdAndTermCode(schoolOrgId, body.termCode().trim()))
            throw new IllegalStateException("Term code already exists for this school");

        Term term = new Term();
        term.schoolOrgId = schoolOrgId;
        term.termCode = body.termCode().trim();
        term.startDate = body.startDate();
        term.endDate = body.endDate();
        term.status = TermStatus.DRAFT;
        Term saved = terms.save(term);

        audit.writeScoped(actorId, "TERM_CREATED", "term_reference", saved.id,
                "SUCCESS", schoolOrgId, "TERM_CREATED");
        return toView(saved);
    }

    /**
     * Transition term status. Allowed transitions:
     *  DRAFT → ACTIVE, DRAFT → CLOSED, ACTIVE → CLOSED.
     */
    @Transactional
    public TermView updateStatus(String actorId, String schoolOrgId,
                                 String termId, UpdateTermStatus body) {
        Term term = terms.findByIdForUpdate(termId)
                .orElseThrow(() -> new IllegalArgumentException("Term not found"));
        if (!term.schoolOrgId.equals(schoolOrgId))
            throw new IllegalArgumentException("Term does not belong to this school");
        if (term.version != body.expectedVersion())
            throw new IllegalStateException("Term has been modified; reload and retry");

        TermStatus from = term.status;
        TermStatus to = body.status();
        validateTransition(from, to);

        term.status = to;
        Term saved = terms.save(term);

        audit.writeScoped(actorId, "TERM_STATUS_CHANGED", "term_reference", saved.id,
                "SUCCESS", schoolOrgId, "STATUS_" + to.name());
        return toView(saved);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void validateTransition(TermStatus from, TermStatus to) {
        boolean allowed = switch (from) {
            case DRAFT  -> to == TermStatus.ACTIVE || to == TermStatus.CLOSED;
            case ACTIVE -> to == TermStatus.CLOSED;
            case CLOSED -> false;
        };
        if (!allowed)
            throw new IllegalArgumentException("Invalid term status transition: " + from + " → " + to);
    }

    private TermView toView(Term t) {
        return new TermView(t.id, t.schoolOrgId, t.termCode,
                t.startDate, t.endDate, t.status, t.version, t.createdAt);
    }

    private void validatePagination(int page, int size) {
        if (page < 0 || page > 10000 || size < 1 || size > 100)
            throw new IllegalArgumentException("Invalid pagination parameters");
    }
}
