package vn.utbridora.auth.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.TestingAuthenticationToken;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.files.FileStorage;
import vn.utbridora.auth.modules.student.application.AcademicCatalogService;
import vn.utbridora.auth.modules.student.port.StudentRosterProvisionPort;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.tokens.InvitationClaims;

class StudentImportServiceListTest {
    private static final String SCHOOL_ID = "11111111-1111-4111-8111-111111111111";
    private static final String TERM_ID = "22222222-2222-4222-8222-222222222222";
    private static final String USER_ID = "33333333-3333-4333-8333-333333333333";

    private StudentImportRuntime runtime(JdbcTemplate jdbc, SchoolDelegationAccess access, TermReferencePort terms, AcademicCatalogService catalogs) {
        return new StudentImportRuntime(
            jdbc, access, mock(AuthorizationPolicy.class), terms, catalogs,
            mock(StudentRosterProvisionPort.class), mock(InvitationClaims.class), mock(OperationLedger.class),
            mock(AuditService.class), Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC), mock(FileStorage.class)
        );
    }

    private TestingAuthenticationToken auth() {
        return new TestingAuthenticationToken(new AppPrincipal(USER_ID, "staff@demo.test", "Staff", 1L, List.of()), null, "ROLE_SCHOOL_STAFF");
    }

    @Test
    void listJobsRequiresSchoolDelegationScopeAndPaginates() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchoolDelegationAccess access = mock(SchoolDelegationAccess.class);
        TermReferencePort terms = mock(TermReferencePort.class);
        AcademicCatalogService catalogs = mock(AcademicCatalogService.class);
        when(terms.findById(TERM_ID)).thenReturn(Optional.of(new TermReferencePort.TermReference(
            TERM_ID, "HK1-2026", SCHOOL_ID, null, null, TermReferencePort.Status.ACTIVE, 1L
        )));
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(SCHOOL_ID), eq(TERM_ID))).thenReturn(1L);
        StudentImportService.Job job = new StudentImportService.Job(
            "44444444-4444-4444-8444-444444444444", SCHOOL_ID, TERM_ID, USER_ID, USER_ID, 1L,
            null, null, "COMPLETED", 2L, "a".repeat(64), "cat-v1", 1L,
            Instant.parse("2026-06-01T00:30:00Z"), 2, 2, null, 0L, null,
            Instant.parse("2026-06-01T00:01:00Z"), Instant.parse("2026-06-01T00:02:00Z")
        );
        when(jdbc.query(anyString(), any(RowMapper.class), eq(SCHOOL_ID), eq(TERM_ID), eq(10), eq(0))).thenReturn(List.of(job));
        when(jdbc.queryForList(anyString(), eq(job.id()))).thenReturn(List.of(Map.of("validation_state", "VALID", "row_state", "PROVISIONED", "selected", true, "n", 2)));

        StudentImportService service = new StudentImportService(runtime(jdbc, access, terms, catalogs));
        var page = service.listJobs(auth(), TERM_ID, 0, 10);

        verify(access).require(any(), eq(SCHOOL_ID), eq(TERM_ID), eq("schoolRosterImport:manage"));
        assertEquals(1, page.totalElements());
        assertEquals("COMPLETED", page.content().getFirst().jobState());
        assertEquals(2, page.content().getFirst().provisionedRows());
    }

    @Test
    void listActiveMajorsFiltersInactiveEntriesAndChecksScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchoolDelegationAccess access = mock(SchoolDelegationAccess.class);
        TermReferencePort terms = mock(TermReferencePort.class);
        AcademicCatalogService catalogs = mock(AcademicCatalogService.class);
        when(terms.findById(TERM_ID)).thenReturn(Optional.of(new TermReferencePort.TermReference(
            TERM_ID, "HK1-2026", SCHOOL_ID, null, null, TermReferencePort.Status.ACTIVE, 1L
        )));
        AcademicCatalogService.Item active = new AcademicCatalogService.Item("SE", "Software Engineering", List.of(), "ACTIVE");
        AcademicCatalogService.Item archived = new AcademicCatalogService.Item("OLD", "Archived", List.of(), "INACTIVE");
        when(catalogs.forProfile(SCHOOL_ID, TERM_ID)).thenReturn(new AcademicCatalogService.Snapshot(
            SCHOOL_ID, TERM_ID, "v1", Map.of(AcademicCatalogService.Kind.MAJOR, List.of(active, archived))
        ));

        StudentImportService service = new StudentImportService(runtime(jdbc, access, terms, catalogs));
        assertEquals(List.of(active), service.listActiveMajors(auth(), TERM_ID));

        doThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Denied", null))
            .when(access).require(any(), eq(SCHOOL_ID), eq(TERM_ID), eq("schoolRosterImport:manage"));
        assertThrows(OperationFailure.class, () -> service.listActiveMajors(auth(), TERM_ID));
    }
}
