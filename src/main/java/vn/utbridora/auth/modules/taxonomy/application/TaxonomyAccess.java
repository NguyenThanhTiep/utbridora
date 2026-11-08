package vn.utbridora.auth.modules.taxonomy.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;

/** Current account, membership and named global TAX assignment; ADMIN defaults cannot grant write. */
@Component
public class TaxonomyAccess {
    private final JdbcTemplate jdbc;
    public TaxonomyAccess(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void requireReader(AppPrincipal actor) {
        requireCurrent(actor);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM memberships m JOIN role_permissions p ON p.role=m.role LEFT JOIN organizations o ON o.id=m.organization_id WHERE m.user_id=? AND m.active=true AND p.permission_code='taxonomy:skills:read' AND ((m.role='ADMIN' AND m.organization_id IS NULL) OR (m.role<>'ADMIN' AND o.status='VERIFIED'))",Integer.class,actor.userId())==0) throw denied();
    }
    public void requireEditor(AppPrincipal actor) {
        requireReader(actor);
        if(jdbc.queryForObject("SELECT COUNT(*) FROM taxonomy_editor_assignments a WHERE a.user_id=? AND a.revoked_at IS NULL AND a.valid_from<=UTC_TIMESTAMP(6) AND UTC_TIMESTAMP(6)<a.valid_until AND EXISTS (SELECT 1 FROM memberships m LEFT JOIN organizations o ON o.id=m.organization_id WHERE m.user_id=a.user_id AND m.active=true AND ((m.role='ADMIN' AND m.organization_id IS NULL) OR (m.role='SCHOOL_STAFF' AND o.type='UNIVERSITY' AND o.status='VERIFIED')))",Integer.class,actor.userId())==0) throw denied();
    }
    private void requireCurrent(AppPrincipal actor) {
        if(actor==null || jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id=? AND status='ACTIVE' AND security_version=?",Integer.class,actor.userId(),actor.securityVersion())==0)
            throw new OperationFailure(401,"SESSION_EXPIRED","Phiên đăng nhập đã hết hiệu lực.",null);
    }
    private static OperationFailure denied() { return new OperationFailure(403,"ACCESS_DENIED","Bạn không có quyền truy cập danh mục theo phạm vi này.",null); }
}
