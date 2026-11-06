
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
