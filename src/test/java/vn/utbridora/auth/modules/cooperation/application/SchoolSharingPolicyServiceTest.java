package vn.utbridora.auth.modules.cooperation.application;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.operations.OperationFailure;

import static org.junit.jupiter.api.Assertions.*;

class SchoolSharingPolicyServiceTest {

    @Test
    void acceptsAllSupportedFields() {
        assertDoesNotThrow(() -> SchoolSharingPolicyService.fields(
            List.of("displayName", "studentCode", "majorCode", "skills",
                    "projects", "certificates", "cv", "evidence", "email", "phone"),
            false
        ));
    }

    @Test
    void acceptsEmptyFieldsWhenAllowed() {
        assertDoesNotThrow(() ->
            SchoolSharingPolicyService.fields(List.of(), true)
        );
    }

    @Test
    void rejectsEmptyFieldsWhenNotAllowed() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(List.of(), false));

        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }

    @Test
    void rejectsNullList() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(null, true));

        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }

    @Test
    void rejectsNullField() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(
                Arrays.asList("skills", null), true));

        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }

    @Test
    void rejectsDuplicateFields() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(
                List.of("skills", "skills"), true));

        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }

    @Test
    void rejectsMoreThanTwelveFields() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(
                List.of("displayName", "studentCode", "majorCode",
                        "skills", "projects", "certificates", "cv",
                        "evidence", "email", "phone", "extra1", "extra2", "extra3"),
                true));

        assertEquals(400, error.status);
        assertEquals("SCHEMA_VALIDATION", error.code);
    }

    @Test
    void rejectsUnsupportedField() {
        OperationFailure error = assertThrows(OperationFailure.class,
            () -> SchoolSharingPolicyService.fields(
                List.of("skills", "salary"), true));

        assertEquals(422, error.status);
        assertEquals("PROFILE_FIELD_UNSUPPORTED", error.code);
    }
}