package vn.utbridora.auth.modules.cooperation.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.modules.cooperation.domain.DemandJson;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.Page;
import vn.utbridora.auth.modules.cooperation.port.OpportunityPort;
import vn.utbridora.auth.modules.student.application.StudentTermScopePort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.user.Role;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service
public class OpportunityService implements OpportunityPort {
    public record PublishOpportunity(Instant publishedAt, Instant closedAt, Long expectedVersion) {}
    private record Row(String cooperationId, String demandId, String schoolOrgId, String enterpriseOrgId,
                       String termId, String enterpriseName, String dataJson, Instant publishedAt,
                       Instant closedAt, long version) {}

    private final JdbcTemplate jdbc;
    private final StudentTermScopePort studentTerms;
    private final AuthorizationPolicy authorization;
    private final SchoolDelegationAccess schoolAccess;
    private final OperationLedger operations;
    private final AuditService audit;

    public OpportunityService(JdbcTemplate jdbc, StudentTermScopePort studentTerms, AuthorizationPolicy authorization,
                              SchoolDelegationAccess schoolAccess, OperationLedger operations, AuditService audit) {
        this.jdbc = jdbc;
        this.studentTerms = studentTerms;
        this.authorization = authorization;
        this.schoolAccess = schoolAccess;
        this.operations = operations;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OpportunityView> listMine(Authentication actor, Query request) {
        page(request.page(), request.size());
        String termId = uuid(request.termId());
        String schoolId = studentSchool(actor, termId);
        String q = request.q() == null ? null : text(request.q(), 150).toLowerCase(Locale.ROOT);
        String major = request.majorCode() == null ? null : request.majorCode();
        if (major != null && !major.matches("[A-Z][A-Z0-9_]{0,63}")) throw failure(400, "SCHEMA_VALIDATION");
        String enterprise = request.enterpriseOrgId() == null ? null : uuid(request.enterpriseOrgId());
        Object[] args = {schoolId, termId, q, q, q, major, major, enterprise, enterprise};
        String where = " FROM cooperations c JOIN demands d ON d.id=c.demand_id JOIN organizations o ON o.id=c.enterprise_org_id " +
            "JOIN organizations s ON s.id=c.school_org_id JOIN opportunity_publications p ON p.cooperation_id=c.id " +
            "WHERE c.state='APPROVED' AND o.status='VERIFIED' AND s.status='VERIFIED' AND c.school_org_id=? AND c.term_id=? " +
            "AND p.published_at<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<p.closed_at " +
            "AND (? IS NULL OR LOWER(JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.title'))) LIKE CONCAT('%',?,'%') OR LOWER(o.name) LIKE CONCAT('%',?,'%')) " +
            "AND (? IS NULL OR JSON_LENGTH(JSON_EXTRACT(d.data_json,'$.required.majorCodes'))=0 " +
            "OR JSON_CONTAINS(JSON_EXTRACT(d.data_json,'$.required.majorCodes'),JSON_QUOTE(?))) " +
            "AND (? IS NULL OR c.enterprise_org_id=?)";
        long total = jdbc.queryForObject("SELECT COUNT(*)" + where, Long.class, args);
        args = java.util.Arrays.copyOf(args, args.length + 2);
        args[args.length - 2] = request.size();
        args[args.length - 1] = (long) request.page() * request.size();
        List<OpportunityView> content = jdbc.query("SELECT c.id,c.demand_id,c.school_org_id,c.enterprise_org_id,c.term_id,o.name AS enterprise_name," +
                "d.data_json,p.published_at,p.closed_at,p.version" + where +
                " ORDER BY p.published_at DESC,c.id ASC LIMIT ? OFFSET ?", (row, index) -> view(row), args);
        return new Page<>(content, request.page(), request.size(), total,
            (int) ((total + request.size() - 1) / request.size()));
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityView readMine(Authentication actor, String opportunityId) {
        Row row = current(uuid(opportunityId), true);
        studentSchool(actor, row.termId());
        return view(row);
    }

    @Override
    @Transactional(readOnly = true)
    public OpportunityView readForPreference(Authentication actor, String opportunityId) {
        Row row = current(uuid(opportunityId), false);
        studentSchool(actor, row.termId());
        return view(row);
    }

    @Transactional
    public OpportunityView publish(Authentication actor, String cooperationId, String idempotencyKey,
                                   PublishOpportunity request) {
        String id = uuid(cooperationId);
        String key = uuid(idempotencyKey);
        if (request.publishedAt() == null || request.closedAt() == null || request.expectedVersion() == null
                || request.expectedVersion() < 0 || request.expectedVersion() > 9_007_199_254_740_991L
                || !request.publishedAt().isBefore(request.closedAt())) {
            throw failure(400, "SCHEMA_VALIDATION");
        }
        Row before = currentForSchool(id);
        schoolAccess.require(actor, before.schoolOrgId(), before.termId(), "opportunity:school:publish");
        return operations.execute(new OperationLedger.Command(actor(actor), before.schoolOrgId(),
                "OPPORTUNITY_PUBLICATION", key, Map.of("cooperationId", id, "request", request), 200),
            OpportunityView.class, () -> {
                Row locked = currentForSchool(id);
                schoolAccess.require(actor, locked.schoolOrgId(), locked.termId(), "opportunity:school:publish");
                if (!locked.cooperationId().equals(id)) throw failure(404, "RESOURCE_NOT_FOUND");
                if (!isApproved(id)) throw failure(409, "STATE_CONFLICT");
                var existing = jdbc.query("SELECT version FROM opportunity_publications WHERE cooperation_id=? FOR UPDATE",
                    (row, index) -> row.getLong(1), id);
                long version = existing.isEmpty() ? 0 : existing.getFirst();
                if (version != request.expectedVersion()) throw failure(409, "VERSION_CONFLICT");
                long next = version + 1;
                version(next);
                if (existing.isEmpty()) {
                    jdbc.update("INSERT INTO opportunity_publications(cooperation_id,published_at,closed_at,version,updated_by,updated_at) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6))",
                        id, utc(request.publishedAt()), utc(request.closedAt()), next, actor(actor));
                } else {
                    jdbc.update("UPDATE opportunity_publications SET published_at=?,closed_at=?,version=?,updated_by=?,updated_at=UTC_TIMESTAMP(6) WHERE cooperation_id=? AND version=?",
                        utc(request.publishedAt()), utc(request.closedAt()), next, actor(actor), id, version);
                }
                String operation = !Instant.now().isBefore(request.closedAt()) ? "CLOSE" : "PUBLISH";
                audit.workflow(actor(actor), "OPPORTUNITY_PUBLICATION_CHANGED", id, locked.schoolOrgId(),
                    Map.of("operation", operation, "version", next, "publishedAt", request.publishedAt().toString(),
                        "closedAt", request.closedAt().toString()));
                return view(current(id, false));
            }, OpportunityView::opportunityId);
    }

    private String studentSchool(Authentication actor, String termId) {
        String student = actor(actor);
        String school = studentTerms.schoolOfLinkedTerm(student, termId)
            .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
        if (!authorization.hasOrganization(actor, school, Role.STUDENT, "student_profile:self:read")) {
            throw failure(404, "RESOURCE_NOT_FOUND");
        }
        return school;
    }

    private Row current(String id, boolean requireOpen) {
        String open = requireOpen ? " AND p.published_at<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<p.closed_at" : "";
        return jdbc.query("SELECT c.id,c.demand_id,c.school_org_id,c.enterprise_org_id,c.term_id,o.name AS enterprise_name," +
                "d.data_json,p.published_at,p.closed_at,p.version FROM cooperations c JOIN demands d ON d.id=c.demand_id " +
                "JOIN organizations o ON o.id=c.enterprise_org_id JOIN organizations s ON s.id=c.school_org_id " +
                "JOIN opportunity_publications p ON p.cooperation_id=c.id WHERE c.id=? AND c.state='APPROVED' " +
                "AND o.status='VERIFIED' AND s.status='VERIFIED'" + open,
            (row, index) -> map(row), id).stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
    }

    private Row currentForSchool(String id) {
        return jdbc.query("SELECT c.id,c.demand_id,c.school_org_id,c.enterprise_org_id,c.term_id,o.name AS enterprise_name," +
                "d.data_json FROM cooperations c JOIN demands d ON d.id=c.demand_id " +
                "JOIN organizations o ON o.id=c.enterprise_org_id " +
                "WHERE c.id=? FOR UPDATE", (row, index) -> new Row(row.getString("id"), row.getString("demand_id"),
                row.getString("school_org_id"), row.getString("enterprise_org_id"), row.getString("term_id"),
                row.getString("enterprise_name"), row.getString("data_json"), null, null, 0), id)
            .stream().findFirst().orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND"));
    }

    private boolean isApproved(String id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cooperations WHERE id=? AND state='APPROVED'",
            Integer.class, id) == 1;
    }

    private static Row map(ResultSet row) throws SQLException {
        return new Row(row.getString("id"), row.getString("demand_id"), row.getString("school_org_id"),
            row.getString("enterprise_org_id"), row.getString("term_id"), row.getString("enterprise_name"),
            row.getString("data_json"), instant(row, "published_at"),
            instant(row, "closed_at"), row.getLong("version"));
    }

    private static OpportunityView view(ResultSet row) throws SQLException {
        return view(map(row));
    }

    private static OpportunityView view(Row row) {
        DemandInput demand = DemandJson.read(row.dataJson(), DemandInput.class);
        return new OpportunityView(row.cooperationId(), row.cooperationId(), row.demandId(), row.schoolOrgId(),
            row.enterpriseOrgId(), row.termId(), row.enterpriseName(), demand.title(),
            demand.required().majorCodes(), demand.required().dateRange(), demand.benefits(), demand.location(),
            row.publishedAt(), row.closedAt(), row.version());
    }
}
