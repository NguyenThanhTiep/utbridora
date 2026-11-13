package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.modules.cooperation.port.OpportunityPort;
import vn.utbridora.auth.student.StudentTermLinkService;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Authoritative T5 provider; the T2 consumer adapter is wired separately. */
@Service
public class JdbcOpportunityRead implements OpportunityPort {
    private record Scope(String schoolOrgId, String termId) {}

    private final JdbcTemplate jdbc;
    private final AuthorizationPolicy core;
    private final StudentTermLinkService studentTerms;

    public JdbcOpportunityRead(JdbcTemplate jdbc, AuthorizationPolicy core,
            StudentTermLinkService studentTerms) {
        this.jdbc = jdbc;
        this.core = core;
        this.studentTerms = studentTerms;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public OpportunityReference readForPreference(ActorContext context, String opportunityId) {
        if (context == null) throw failure(401, "SESSION_EXPIRED");
        String student = actor(context.authentication());
        String id = uuid(opportunityId);
        var scope = jdbc.query("SELECT school_org_id,term_id FROM cooperations WHERE id=?",
                (row, index) -> new Scope(row.getString("school_org_id"), row.getString("term_id")), id)
                .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        if (context.organizationId() != null && !context.organizationId().equals(scope.schoolOrgId())
                || !core.holdOrganization(context.authentication(), scope.schoolOrgId(),
                    Role.STUDENT, "student_profile:self:read")
                || !studentTerms.linked(student, scope.schoolOrgId(), scope.termId()))
            throw failure(404, "RESOURCE_NOT_FOUND");

        return jdbc.query("""
                SELECT c.id,c.school_org_id,c.enterprise_org_id,c.term_id,
                       c.published_at,c.closed_at,c.version
                FROM cooperations c JOIN demands d ON d.id=c.demand_id
                WHERE c.id=? AND c.school_org_id=? AND c.term_id=?
                  AND c.state='APPROVED' AND d.soft_deleted_at IS NULL
                  AND c.published_at IS NOT NULL AND c.closed_at IS NOT NULL
                FOR SHARE
                """, (row, index) -> new OpportunityReference(row.getString("id"),
                    row.getString("school_org_id"), row.getString("enterprise_org_id"),
                    row.getString("term_id"), instant(row, "published_at"),
                    instant(row, "closed_at"), row.getLong("version")),
                id, scope.schoolOrgId(), scope.termId())
                .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
    }
}
