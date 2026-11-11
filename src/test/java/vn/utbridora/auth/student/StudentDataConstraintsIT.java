
package vn.utbridora.auth.student;

import java.sql.*;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

import static org.junit.jupiter.api.Assertions.*;

class StudentDataConstraintsIT {

    @Test
    void studentRosterAndEligibilityConstraints() throws Exception {
        try (var mysql = new MySQLContainer<>("mysql:8.4")) {
            mysql.start();

            Flyway.configure()
                    .dataSource(
                            mysql.getJdbcUrl(),
                            mysql.getUsername(),
                            mysql.getPassword()
                    )
                    .load()
                    .migrate();

            try (Connection c = DriverManager.getConnection(
                    mysql.getJdbcUrl(),
                    mysql.getUsername(),
                    mysql.getPassword()
            )) {
                String schoolA = UUID.randomUUID().toString();
                String schoolB = UUID.randomUUID().toString();
                String rosterA = UUID.randomUUID().toString();
                String rosterB = UUID.randomUUID().toString();

                // Tạo 2 trường để thỏa mãn khóa ngoại
                insertSchool(c, schoolA);
                insertSchool(c, schoolB);

                // MSSV 00123 giữ nguyên số 0 đầu
                insertRoster(c, rosterA, schoolA, "00123");

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT student_code FROM student_rosters WHERE id = ?"
                )) {
                    ps.setString(1, rosterA);

                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("00123", rs.getString(1));
                    }
                }

                // Cùng trường không được trùng MSSV
                assertThrows(SQLException.class, () ->
                        insertRoster(
                                c,
                                UUID.randomUUID().toString(),
                                schoolA,
                                "00123"
                        )
                );

                // Khác trường được phép có cùng MSSV
                assertDoesNotThrow(() ->
                        insertRoster(c, rosterB, schoolB, "00123")
                );
                // Truy vấn theo trường A chỉ trả về roster A
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT id
                        FROM student_rosters
                        WHERE school_org_id = ?
                        """
                )) {
                    ps.setString(1, schoolA);

                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(rosterA, rs.getString("id"));
                        assertFalse(rs.next());
                    }
                }

                // Không tìm thấy roster B khi lọc theo trường A
                try (PreparedStatement ps = c.prepareStatement(
                        """
                        SELECT id
                        FROM student_rosters
                        WHERE id = ? AND school_org_id = ?
                        """
                )) {
                    ps.setString(1, rosterB);
                    ps.setString(2, schoolA);

                    try (ResultSet rs = ps.executeQuery()) {
                        assertFalse(rs.next());
                    }
                }


                // Eligibility mặc định chưa được duyệt
                String eligibilityId = UUID.randomUUID().toString();

                insertEligibility(
                        c,
                        eligibilityId,
                        rosterA,
                        "2026-HK1"
                );

                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT school_approved " +
                        "FROM student_term_eligibilities WHERE id = ?"
                )) {
                    ps.setString(1, eligibilityId);

                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertFalse(rs.getBoolean(1));
                    }
                }

                // Một sinh viên không được trùng Eligibility trong kỳ
                assertThrows(SQLException.class, () ->
                        insertEligibility(
                                c,
                                UUID.randomUUID().toString(),
                                rosterA,
                                "2026-HK1"
                        )
                );
                // A canonical binding must agree on student, school, term UUID and term code.
                String user=UUID.randomUUID().toString(),termA=UUID.randomUUID().toString(),termB=UUID.randomUUID().toString();
                try(var sql=c.prepareStatement("INSERT INTO users (id,email,password_hash,display_name,status,security_version,failed_attempts,created_at,updated_at) VALUES (?,?,'!NO_LOGIN!','Synthetic','ACTIVE',0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")) {
                    sql.setString(1,user);sql.setString(2,user+"@example.test");sql.executeUpdate();
                }
                try(var sql=c.prepareStatement("UPDATE student_rosters SET user_id=? WHERE id=?")) {sql.setString(1,user);sql.setString(2,rosterA);sql.executeUpdate();}
                try(var sql=c.prepareStatement("INSERT INTO terms (id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,'2027-A','2027-01-01','2027-04-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))")) {
                    sql.setString(1,termA);sql.setString(2,schoolA);sql.executeUpdate();
                    sql.setString(1,termB);sql.setString(2,schoolB);sql.executeUpdate();
                }
                String boundId=UUID.randomUUID().toString();
                try(var sql=c.prepareStatement("INSERT INTO student_term_eligibilities (id,student_roster_id,term_code,user_id,school_org_id,term_id,created_at) VALUES (?,?,'2027-A',?,?,?,UTC_TIMESTAMP(6))")) {
                    sql.setString(1,boundId);sql.setString(2,rosterA);sql.setString(3,user);sql.setString(4,schoolA);sql.setString(5,termB);
                    assertThrows(SQLException.class,sql::executeUpdate);
                    sql.setString(5,termA);assertEquals(1,sql.executeUpdate());
                }
                try(var sql=c.prepareStatement("UPDATE student_term_eligibilities SET school_approved=b'1' WHERE id=?")) {
                    sql.setString(1,boundId);assertThrows(SQLException.class,sql::executeUpdate);
                }
            }
        }
    }

    private void insertSchool(Connection c, String id)
            throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO organizations
(id, name, type, status, created_at)
VALUES (?, ?, 'UNIVERSITY', 'VERIFIED',
        UTC_TIMESTAMP(6))
                """
        )) {
            ps.setString(1, id);
            ps.setString(2, "Test University " + id.substring(0, 8));
            ps.executeUpdate();
        }
    }

    private void insertRoster(
            Connection c,
            String id,
            String schoolId,
            String studentCode
    ) throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO student_rosters
                (id, school_org_id, student_code,
                 full_name, email, major_code,
                 term_code, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(6))
                """
        )) {
            ps.setString(1, id);
            ps.setString(2, schoolId);
            ps.setString(3, studentCode);
            ps.setString(4, "Test Student");
            ps.setString(5, id + "@example.test");
            ps.setString(6, "CNTT");
            ps.setString(7, "2026-HK1");
            ps.executeUpdate();
        }
    }

    private void insertEligibility(
            Connection c,
            String id,
            String rosterId,
            String termCode
    ) throws SQLException {

        try (PreparedStatement ps = c.prepareStatement(
                """
                INSERT INTO student_term_eligibilities
                (id, student_roster_id, term_code, created_at)
                VALUES (?, ?, ?, UTC_TIMESTAMP(6))
                """
        )) {
            ps.setString(1, id);
            ps.setString(2, rosterId);
            ps.setString(3, termCode);
            ps.executeUpdate();
        }
    }
}
