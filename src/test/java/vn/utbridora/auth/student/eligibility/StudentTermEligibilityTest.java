
package vn.utbridora.auth.student.eligibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentTermEligibilityTest {

    @Test
    void newEligibilityShouldNotBeApproved() {
        StudentTermEligibility eligibility =
                new StudentTermEligibility();

        assertFalse(
                eligibility.schoolApproved,
                "Sinh vien moi khong duoc tu dong duyet"
        );
    }

    @Test
    void shouldKeepStudentAndTermReferences() {
        StudentTermEligibility eligibility =
                new StudentTermEligibility();

        eligibility.studentRosterId = "student-001";
        eligibility.termCode = "2026-HK1";

        assertEquals(
                "student-001",
                eligibility.studentRosterId
        );

        assertEquals(
                "2026-HK1",
                eligibility.termCode
        );
    }

    @Test
    void shouldGenerateIdAndCreatedAtBeforePersist() {
        StudentTermEligibility eligibility =
                new StudentTermEligibility();

        eligibility.onCreate();

        assertNotNull(eligibility.id);
        assertNotNull(eligibility.createdAt);
        assertFalse(eligibility.schoolApproved);
    }
}
