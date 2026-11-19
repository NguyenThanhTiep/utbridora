package vn.utbridora.auth.onboarding;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.files.FileStorage;
import vn.utbridora.auth.modules.student.application.AcademicCatalogService;
import vn.utbridora.auth.modules.student.port.StudentRosterProvisionPort;
import vn.utbridora.auth.operations.OperationLedger;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.tokens.InvitationClaims;

/** Immutable collaborators, not an AOP-proxied workflow. Never read fields off a Spring proxy. */
@Component
record StudentImportRuntime(JdbcTemplate jdbc,SchoolDelegationAccess access,AuthorizationPolicy permissions,
    TermReferencePort terms,AcademicCatalogService catalogs,StudentRosterProvisionPort rosters,InvitationClaims claims,
    OperationLedger ledger,AuditService audit,@Qualifier("onboardingClock") Clock clock,FileStorage storage) {}
