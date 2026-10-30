package vn.utbridora.auth.authorization;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.utbridora.auth.user.Role;

/** DB-backed role/permission catalog, migrated and versioned with Flyway. */
@Repository
public class PermissionCatalog {
    private final JdbcTemplate jdbc;
    public PermissionCatalog(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public boolean granted(Role role, String permission) {
        Integer count=jdbc.queryForObject("""
            SELECT count(*) FROM role_permissions
            WHERE role=? AND permission_code=?
            """, Integer.class, role.name(), permission);
        return count != null && count > 0;
    }
    public List<String> permissions(Role role) {
        return jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role=? ORDER BY permission_code",
            String.class, role.name());
    }
}
