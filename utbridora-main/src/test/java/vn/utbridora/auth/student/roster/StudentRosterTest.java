
package vn.utbridora.auth.student.roster;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StudentRosterTest {

    @Test
    void studentCodeShouldPreserveLeadingZeros() {
        StudentRoster roster = new StudentRoster();

        roster.studentCode = "00123";

        assertEquals("00123", roster.studentCode);
    }

    @Test
    void shouldGenerateIdAndCreatedAtBeforePersist() {
        StudentRoster roster = new StudentRoster();

        roster.onCreate();

        assertNotNull(roster.id);
        assertNotNull(roster.createdAt);
        assertDoesNotThrow(() -> Instant.parse(
                roster.createdAt.toString()
        ));
    }

    @Test
    void shouldNotOverwriteExistingIdAndCreatedAt() {
        StudentRoster roster = new StudentRoster();

        roster.id = "existing-id";
        roster.createdAt = Instant.parse(
                "2026-01-01T00:00:00Z"
        );

        roster.onCreate();

        assertEquals("existing-id", roster.id);
        assertEquals(
                Instant.parse("2026-01-01T00:00:00Z"),
                roster.createdAt
        );
    }
}
