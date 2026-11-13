package vn.utbridora.auth.modules.internship.infrastructure;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.modules.internship.application.SubmissionPorts.*;
import vn.utbridora.auth.modules.internship.domain.*;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.Role;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** JDBC/authorization/wiring UNIT tests. Mock SQL and Connection are not MySQL evidence.
 * No migration is loaded/applied and no real user, Placement or File provider is fabricated.
 */
class SubmissionRuntimeTest {
    private static final UUID ASSIGNMENT=id(1), STUDENT=id(2), CASE=id(3), TASK=id(4), GROUP=id(5),
            COHORT=id(6), SCHOOL=id(7), ENTERPRISE=id(8), TERM=id(9), SUBMISSION=id(10);
    private static final Instant NOW=Instant.parse("2026-11-10T06:00:00.123456Z");
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final OperationLedger ledger=mock(OperationLedger.class);
    private final AuditService audit=mock(AuditService.class);
    private final JdbcSubmissionSession session=new JdbcSubmissionSession(jdbc,ledger,audit);
    private final AuthorizationPolicy core=mock(AuthorizationPolicy.class);
    private final WorkspacePorts.Access workspace=mock(WorkspacePorts.Access.class);
    private final CoreSubmissionAccess access=new CoreSubmissionAccess(core,workspace);
    private final Authentication actor=auth(STUDENT);

    @BeforeEach void transactionActive() { TransactionSynchronizationManager.setActualTransactionActive(true); }
    @AfterEach void clearTransaction() { TransactionSynchronizationManager.clear(); }

    @Test void jdbcRefusesExecutionOutsideTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertThrows(IllegalStateException.class,()->session.assignment(ASSIGNMENT));
        assertThrows(IllegalStateException.class,session::requireBaseline);
        verifyNoInteractions(jdbc,ledger,audit);
    }

    @Test void missingBaselineNeverTouchesSubmissionTables() {
        baseline(List.of());
        error(503,"DEPENDENCY_UNAVAILABLE",session::requireBaseline);
        verify(jdbc,never()).query(startsWith("SELECT id,assignment_id"),any(RowMapper.class));
    }

    @ParameterizedTest @ValueSource(strings={"missing28","reversed","wrongScript","missing29"})
    void onlyOrderedActualV28ThenV29IsAccepted(String variant) {
        var v28=List.of("28","V28__private_file_service.sql");
        var v29=List.of("29","V29__t3_submission_review_comment.sql");
        baseline(switch(variant) {
            case "missing28" -> List.of(v29);
            case "missing29" -> List.of(v28);
            case "reversed" -> List.of(v29,v28);
            default -> List.of(v28,List.of("29","different.sql"));
        });
        error(503,"DEPENDENCY_UNAVAILABLE",session::requireBaseline);
    }

    @Test void schemaQueryFailureIsSafeAndNeverTriggersDDL() {
        when(jdbc.query(startsWith("SELECT version,script"),any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("test","not exposed",new SQLException("synthetic")));
        error(503,"DEPENDENCY_UNAVAILABLE",session::requireBaseline);
        verify(jdbc,never()).execute(anyString());verifyNoInteractions(ledger,audit);
    }

    @Test void readyBaselineStillChecksTheExpectedColumns() {
        ready();session.requireBaseline();
        verify(jdbc).query(contains("WHERE 1=0"),any(RowMapper.class));
    }

    @Test void recursiveMainLocationsKeepTheOriginalBaselineIdentity() {
        baseline(List.of(List.of("28","main/V28__private_file_service.sql"),
                List.of("29","main/V29__t3_submission_review_comment.sql")));
        session.requireBaseline();
        verify(jdbc).query(contains("WHERE 1=0"),any(RowMapper.class));
    }

    @Test void knownLegacyLineageNeedsSuccessfulConvergenceAndActualColumns() {
        baseline(List.of(List.of("28","V28__matching_protected_releases.sql"),
                List.of("29","V29__matching_audit_statistics.sql")));
        when(jdbc.queryForObject(contains("version='32'"),eq(Integer.class))).thenReturn(1);
        session.requireBaseline();
        verify(jdbc).query(contains("WHERE 1=0"),any(RowMapper.class));
        when(jdbc.query(contains("WHERE 1=0"),any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("test","not exposed",new SQLException("synthetic")));
        error(503,"DEPENDENCY_UNAVAILABLE",session::requireBaseline);
    }

    @Test void acceptedVersionNumbersDoNotHideMissingSubmissionColumns() {
        ready();
        when(jdbc.query(contains("WHERE 1=0"),any(RowMapper.class)))
                .thenThrow(new BadSqlGrammarException("test","not exposed",new SQLException("synthetic")));
        error(503,"DEPENDENCY_UNAVAILABLE",session::requireBaseline);
    }

    @Test void assignmentProjectionUsesStoredScopeAndOnlyTheRequestedAssignment() throws Exception {
        contextQuery();var result=session.assignment(ASSIGNMENT);
        assertEquals(context(),result);assertEquals(List.of(result.assignment()),result.task().value().assignments());
        verify(jdbc).query(contains("WHERE a.id=?"),any(RowMapper.class),eq(ASSIGNMENT.toString()));
        verifyNoInteractions(ledger,audit);
    }

    @Test void missingAssignmentIsNotReplacedWithASyntheticContext() {
        assertNull(session.assignment(ASSIGNMENT));assertNull(session.lockAssignment(ASSIGNMENT));
    }

    @Test void lockKeepsTaskDeadlineAndAssignmentCurrentThroughCommit() throws Exception {
        contextQuery();locks();var result=session.lockAssignment(ASSIGNMENT);assertEquals(context(),result);
        var order=inOrder(jdbc);
        order.verify(jdbc).queryForList(startsWith("SELECT task_id"),eq(String.class),eq(ASSIGNMENT.toString()));
        order.verify(jdbc).queryForList(contains("internship_tasks WHERE id=? FOR SHARE"),eq(String.class),eq(TASK.toString()));
        order.verify(jdbc).queryForList(contains("WHERE id=? FOR UPDATE"),eq(String.class),eq(ASSIGNMENT.toString()));
        order.verify(jdbc).query(argThat(sql->sql.contains("WHERE a.id=?")&&!sql.contains("FOR SHARE")&&!sql.contains("FOR UPDATE")),
                any(RowMapper.class),eq(ASSIGNMENT.toString()));
    }

    @Test void changedTaskIdentityUnderLockFailsClosed() {
        locks();when(jdbc.queryForList(contains("WHERE id=? FOR UPDATE"),eq(String.class),eq(ASSIGNMENT.toString())))
                .thenReturn(List.of(id(99).toString()));
        error(503,"DEPENDENCY_UNAVAILABLE",()->session.lockAssignment(ASSIGNMENT));verifyNoInteractions(audit,ledger);
    }

    @Test void immutableUrlSnapshotPreservesNullableNoteAndUtcMicroseconds() throws Exception {
        artifactQuery(null);assertEquals(submission(),session.submission(SUBMISSION));
        verify(jdbc,never()).update(anyString());
    }

    @Test void fileModeRowCannotBeMisrepresentedAsAnEmptyFileList() throws Exception {
        artifactQuery("file");error(503,"DEPENDENCY_UNAVAILABLE",()->session.submission(SUBMISSION));
    }

    @Test void firstUrlInsertUsesBoundFieldsAndExpectedVersionCas() {
        insertSucceeds();session.appendFirst(context().assignment(),submission());
        var order=inOrder(jdbc);
        order.verify(jdbc).queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(ASSIGNMENT.toString()));
        order.verify(jdbc).update(startsWith("INSERT INTO internship_submissions"),eq(SUBMISSION.toString()),eq(ASSIGNMENT.toString()),
                eq(1),eq("https://example.test/v1"),isNull(),eq(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC)),eq(false));
        order.verify(jdbc).update(contains("state='IN_PROGRESS' AND current_submission_id IS NULL AND version<9007199254740991"),
                eq(SUBMISSION.toString()),eq(ASSIGNMENT.toString()),eq(2L));
        verifyNoInteractions(audit); // Service owns mandatory audit after append; no second hidden audit.
    }

    @Test void existingHistoryStopsFirstInsertEvenWithNullCurrentPointer() {
        when(jdbc.queryForObject(startsWith("SELECT EXISTS"),eq(Boolean.class),eq(ASSIGNMENT.toString()))).thenReturn(true);
        error(409,"STATE_CONFLICT",()->session.appendFirst(context().assignment(),submission()));
        verify(jdbc,never()).update(anyString(),any(),any(),any());
    }

    @Test void lostCasPropagatesConflictForTheOuterTransactionToRollback() {
        error(409,"VERSION_CONFLICT",()->session.appendFirst(context().assignment(),submission()));
        verifyNoInteractions(audit);
    }

    @Test void uniqueCollisionDoesNotAdvanceAssignment() {
        when(jdbc.update(startsWith("INSERT INTO internship_submissions"),any(),any(),any(),any(),any(),any(),any()))
                .thenThrow(new DuplicateKeyException("synthetic"));
        error(409,"STATE_CONFLICT",()->session.appendFirst(context().assignment(),submission()));
        verify(jdbc,never()).update(startsWith("UPDATE"),any(),any(),any());
    }

    @Test void maxAssignmentVersionCannotInsert() {
        var a=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.IN_PROGRESS,9_007_199_254_740_991L,null);
        error(409,"VERSION_CONFLICT",()->session.appendFirst(a,submission()));verifyNoInteractions(jdbc);
    }

    @Test void wrongAssignmentArtifactCannotBeAppended() {
        var row=new SubmissionDto(SUBMISSION,id(88),1,List.of(),"https://example.test/v1",null,NOW,false);
        error(503,"DEPENDENCY_UNAVAILABLE",()->session.appendFirst(context().assignment(),row));verifyNoInteractions(jdbc);
    }

    @Test void genericLedgerDelegatesAllArgumentsAndReturnsCoreResponse() {
        var command=command();Supplier<SubmissionDto> mutation=SubmissionRuntimeTest::submission;
        Function<SubmissionDto,String> effect=r->r.submissionId().toString();
        when(ledger.execute(command,SubmissionDto.class,mutation,effect)).thenReturn(submission());
        assertEquals(submission(),session.idempotent(command,SubmissionDto.class,mutation,effect));
        verify(ledger).execute(command,SubmissionDto.class,mutation,effect);verifyNoInteractions(jdbc,audit);
    }

    @Test void ledgerFailureIsNotConvertedToASuccessOrRetriedLocally() {
        var command=command();Supplier<SubmissionDto> mutation=mock(Supplier.class);
        when(ledger.execute(eq(command),eq(SubmissionDto.class),eq(mutation),any())).thenThrow(failure(503,"TRANSIENT_FAILURE"));
        error(503,"TRANSIENT_FAILURE",()->session.idempotent(command,SubmissionDto.class,mutation,r->r.submissionId().toString()));
        verifyNoInteractions(mutation,audit,jdbc);
    }

    @Test void createdAuditUsesStoredSchoolAndSubmissionResourceAndPropagatesFailure() {
        var metadata=Map.<String,Object>of("assignmentId",ASSIGNMENT.toString(),"versionNo",1,"fileIds",List.of(),"lateFlag",false);
        session.audit("SUBMISSION_CREATED",STUDENT,SCHOOL,SUBMISSION,metadata);
        verify(audit).workflow(STUDENT.toString(),"SUBMISSION_CREATED",SUBMISSION.toString(),SCHOOL.toString(),metadata);
        doThrow(failure(500,"AUDIT_WRITE_FAILED")).when(audit).workflow(anyString(),anyString(),anyString(),anyString(),anyMap());
        error(500,"AUDIT_WRITE_FAILED",()->session.audit("SUBMISSION_CREATED",STUDENT,SCHOOL,SUBMISSION,metadata));
    }

    @Test void absentReviewAuthorityCannotBeBypassed() {
        error(503,"DEPENDENCY_UNAVAILABLE",()->access.requireReview(actor,context()));
        verifyNoInteractions(jdbc,ledger,audit,core,workspace);
    }

    @Test void reviewedAuditUsesTheCanonicalActionWithoutAnAlias() {
        var metadata=Map.<String,Object>of("assignmentId",ASSIGNMENT.toString(),"reviewedVersion",1,
                "verdict","ACCEPT","assignmentVersion",4L);
        session.audit("SUBMISSION_REVIEWED",STUDENT,SCHOOL,SUBMISSION,metadata);
        verify(audit).workflow(STUDENT.toString(),"SUBMISSION_REVIEWED",SUBMISSION.toString(),SCHOOL.toString(),metadata);
    }

    private WorkspaceAuthority.Assignment mentorAssignment(String kind,UUID school,UUID enterprise,UUID term,UUID caseId) {
        return new WorkspaceAuthority.Assignment(id(80).toString(),kind,STUDENT.toString(),
                new WorkspaceAuthority.Scope(school.toString(),enterprise.toString(),term.toString(),GROUP.toString(),caseId==null?null:caseId.toString()),
                true,NOW.minusSeconds(60),NOW.plusSeconds(60),1L,id(81).toString(),"SYNTHETIC");
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void reviewRequiresExactWritePermissionAndCurrentMentorOrHandover(boolean authorized) {
        var authority=mock(WorkspaceAuthority.class);var reviewAccess=new CoreSubmissionAccess(core,workspace,authority);var target=context();
        when(core.holdOrganization(actor,ENTERPRISE.toString(),Role.ENTERPRISE,"internship:enterprise:task_write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(mentorAssignment("MENTOR",SCHOOL,ENTERPRISE,TERM,CASE));
        when(authority.canReadTask(actor,target.cohort().scope(),target.task(),CASE)).thenReturn(authorized);
        if(authorized)reviewAccess.requireReview(actor,target);else error(403,"ACCESS_DENIED",()->reviewAccess.requireReview(actor,target));
        verify(authority).canReadTask(actor,target.cohort().scope(),target.task(),CASE);
    }
    @ParameterizedTest @ValueSource(strings={"HR","COORDINATOR","ADMIN","STUDENT"})
    void nonMentorCannotReviewEvenWithAWritePermission(String kind) {
        var authority=mock(WorkspaceAuthority.class);var reviewAccess=new CoreSubmissionAccess(core,workspace,authority);
        when(core.holdOrganization(actor,ENTERPRISE.toString(),Role.ENTERPRISE,"internship:enterprise:task_write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(mentorAssignment(kind,SCHOOL,ENTERPRISE,TERM,CASE));
        error(403,"ACCESS_DENIED",()->reviewAccess.requireReview(actor,context()));
        verify(authority,never()).canReadTask(any(),any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"school","enterprise","term","case"})
    void mentorReviewRejectsEveryForeignScopeDimension(String dimension) {
        var authority=mock(WorkspaceAuthority.class);var reviewAccess=new CoreSubmissionAccess(core,workspace,authority);
        when(core.holdOrganization(actor,ENTERPRISE.toString(),Role.ENTERPRISE,"internship:enterprise:task_write")).thenReturn(true);
        when(authority.selected(actor)).thenReturn(mentorAssignment("MENTOR",dimension.equals("school")?id(99):SCHOOL,
                dimension.equals("enterprise")?id(99):ENTERPRISE,dimension.equals("term")?id(99):TERM,dimension.equals("case")?id(99):CASE));
        error(404,"RESOURCE_NOT_FOUND",()->reviewAccess.requireReview(actor,context()));
    }
    @Test void missingReviewPermissionStopsBeforeResolvingAnyBusinessAssignment() {
        var authority=mock(WorkspaceAuthority.class);var reviewAccess=new CoreSubmissionAccess(core,workspace,authority);
        error(403,"ACCESS_DENIED",()->reviewAccess.requireReview(actor,context()));verifyNoInteractions(authority);
    }
    @Test void reviewInsertIsImmutableAndCasDoesNotChangeTheCurrentArtifactPointer() {
        var expected=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.SUBMITTED,1001L,SUBMISSION);
        var updated=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.COMPLETED,1002L,SUBMISSION);
        var value=new ReviewDto(id(80),SUBMISSION,1,ReviewVerdict.ACCEPT,"Accepted",id(81),NOW,1002L);
        when(jdbc.update(startsWith("UPDATE internship_task_assignments SET state=?"),any(),any(),any(),any())).thenReturn(1);
        session.appendReview(expected,updated,value);
        verify(jdbc).update(startsWith("INSERT INTO internship_submission_reviews"),eq(id(80).toString()),eq(SUBMISSION.toString()),eq(1),eq("ACCEPT"),eq("Accepted"),eq(id(81).toString()),eq(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC)),eq(1002L));
        verify(jdbc).update(contains("state='SUBMITTED' AND current_submission_id=?"),eq("COMPLETED"),eq(ASSIGNMENT.toString()),eq(1001L),eq(SUBMISSION.toString()));
        verify(jdbc,never()).update(contains("UPDATE internship_submissions"),any(Object[].class));
    }
    @Test void duplicateReviewInsertCannotUpdateTheAssignment() {
        var expected=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.SUBMITTED,3L,SUBMISSION);
        var updated=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.COMPLETED,4L,SUBMISSION);
        var value=new ReviewDto(id(80),SUBMISSION,1,ReviewVerdict.ACCEPT,"Accepted",id(81),NOW,4L);
        when(jdbc.update(startsWith("INSERT INTO internship_submission_reviews"),any(),any(),any(),any(),any(),any(),any(),any())).thenThrow(new DuplicateKeyException("synthetic"));
        error(409,"STATE_CONFLICT",()->session.appendReview(expected,updated,value));
        verify(jdbc,never()).update(startsWith("UPDATE"),any(),any(),any(),any());
    }
    @Test void revisionCasComparesThePreviousPointerAndKeepsTheOlderRowUntouched() {
        var expected=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.IN_PROGRESS,5L,SUBMISSION);
        var next=new SubmissionDto(id(80),ASSIGNMENT,2,List.of(),"https://example.test/v2",null,NOW,false);
        when(jdbc.update(startsWith("UPDATE internship_task_assignments"),any(),any(),any(),any())).thenReturn(1);
        session.appendRevision(expected,submission(),next);
        verify(jdbc).update(contains("state='IN_PROGRESS' AND current_submission_id=?"),eq(id(80).toString()),eq(ASSIGNMENT.toString()),eq(5L),eq(SUBMISSION.toString()));
        verify(jdbc).update(startsWith("INSERT INTO internship_submissions"),eq(id(80).toString()),eq(ASSIGNMENT.toString()),eq(2),eq(next.url()),isNull(),eq(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC)),eq(false));
    }
    @Test void revisionCasFailureMustEscapeToTheTransactionBoundary() {
        var expected=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.IN_PROGRESS,5L,SUBMISSION);
        var next=new SubmissionDto(id(80),ASSIGNMENT,2,List.of(),"https://example.test/v2",null,NOW,false);
        error(409,"VERSION_CONFLICT",()->session.appendRevision(expected,submission(),next));verifyNoInteractions(audit);
    }
    @Test void revisionDoesNotAliasMissingCoreAuditMappingToCreated() {
        doThrow(new IllegalArgumentException("Missing canonical mapping")).when(audit).workflow(anyString(),eq("SUBMISSION_REVISED"),anyString(),anyString(),anyMap());
        error(503,"DEPENDENCY_UNAVAILABLE",()->session.audit("SUBMISSION_REVISED",STUDENT,SCHOOL,SUBMISSION,Map.of()));
        verify(audit,never()).workflow(anyString(),eq("SUBMISSION_CREATED"),anyString(),anyString(),anyMap());
    }

    @Test void historyLocksAssignmentBeforeScopedCountAndPageAndNeverRebuildsFromPointer() throws Exception {
        when(jdbc.queryForList(contains("SELECT id FROM internship_task_assignments"),eq(String.class),eq(ASSIGNMENT.toString())))
                .thenReturn(List.of(ASSIGNMENT.toString()));
        when(jdbc.queryForObject(startsWith("SELECT COUNT"),eq(Long.class),eq(ASSIGNMENT.toString()))).thenReturn(1L);
        when(jdbc.query(contains("ORDER BY version_no DESC,id ASC LIMIT ? OFFSET ?"),any(RowMapper.class),eq(ASSIGNMENT.toString()),eq(25),eq(50L)))
                .thenReturn(List.of());
        var page=session.history(ASSIGNMENT,2,25);assertEquals(1L,page.totalElements());assertTrue(page.content().isEmpty());
        var order=inOrder(jdbc);
        order.verify(jdbc).queryForList(endsWith("FOR SHARE"),eq(String.class),eq(ASSIGNMENT.toString()));
        order.verify(jdbc).queryForObject(startsWith("SELECT COUNT"),eq(Long.class),eq(ASSIGNMENT.toString()));
        order.verify(jdbc).query(contains("ORDER BY version_no DESC,id ASC"),any(RowMapper.class),eq(ASSIGNMENT.toString()),eq(25),eq(50L));
        verifyNoInteractions(ledger,audit);
    }

    @Test void historyRejectsMissingAssignment() {
        error(404,"RESOURCE_NOT_FOUND",()->session.history(ASSIGNMENT,0,25));
    }

    @Test void submitRequiresCurrentSelfAndExactSchoolStudentPermission() {
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(true);
        when(core.holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"internship:student:submission_write")).thenReturn(true);
        access.requireSubmit(actor,context());
        verify(core).holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"internship:student:submission_write");
        verifyNoInteractions(workspace);
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void foreignOrRevokedSubmitterCannotWrite(boolean revoked) {
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(revoked);
        error(404,"RESOURCE_NOT_FOUND",()->access.requireSubmit(actor,context()));verifyNoInteractions(workspace);
    }

    @Test void permissionInAnotherSchoolDoesNotAuthorizeTheStoredAssignmentScope() {
        var original=context();var differentSchool=id(99);
        var otherCohort=new Cohort(COHORT,new WorkspaceScope(ENTERPRISE,differentSchool,TERM),"C1","Cohort",1);
        var target=new AssignmentContext(otherCohort,original.group(),original.task(),original.assignment());
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(true);
        when(core.holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"internship:student:submission_write")).thenReturn(true);
        error(404,"RESOURCE_NOT_FOUND",()->access.requireSubmit(actor,target));
        verify(core).holdOrganization(actor,differentSchool.toString(),Role.STUDENT,"internship:student:submission_write");
        verifyNoInteractions(workspace);
    }

    @Test void anonymousRequestCannotReachCoreChecks() {
        error(401,"SESSION_EXPIRED",()->access.requireSubmit(null,context()));
        error(401,"SESSION_EXPIRED",()->access.requireRead(null,context()));verifyNoInteractions(core,workspace);
    }

    @Test void ownerHistoryUsesReadPermissionWithoutRequiringActivePlacement() {
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(true);
        when(core.holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"t3:workspace:read")).thenReturn(true);
        access.requireRead(actor,context());verifyNoInteractions(workspace);
    }

    @Test void revokedHistoryOwnerCannotFallThroughToAnotherRole() {
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(true);
        error(404,"RESOURCE_NOT_FOUND",()->access.requireRead(actor,context()));verifyNoInteractions(workspace);
        verify(core,never()).holdOrganization(eq(actor),anyString(),eq(Role.ENTERPRISE),anyString());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void nonOwnerHistoryRequiresBothCurrentScopedPermissionAndExistingViewerAuthority(boolean assigned) {
        var mentor=auth(id(80));when(core.isSelf(mentor,id(80).toString())).thenReturn(true);
        when(core.holdOrganization(mentor,ENTERPRISE.toString(),Role.ENTERPRISE,"t3:workspace:read")).thenReturn(true);
        var target=context();when(workspace.canReadAssignment(mentor,target.cohort(),target.task(),CASE)).thenReturn(assigned);
        if(assigned) access.requireRead(mentor,target);else error(404,"RESOURCE_NOT_FOUND",()->access.requireRead(mentor,target));
        verify(workspace).canReadAssignment(mentor,target.cohort(),target.task(),CASE);
    }

    @Test void roleOrBusinessAssignmentAloneDoesNotGrantHistory() {
        var foreign=auth(id(80));when(core.isSelf(foreign,id(80).toString())).thenReturn(true);
        error(404,"RESOURCE_NOT_FOUND",()->access.requireRead(foreign,context()));verifyNoInteractions(workspace);
    }

    @Test void staleIdentityCannotReadEvenWithAnOldRoleGrant() {
        error(404,"RESOURCE_NOT_FOUND",()->access.requireRead(actor,context()));
        verify(core,never()).holdOrganization(any(),anyString(),any(),anyString());
    }

    @ParameterizedTest @ValueSource(strings={"audit","ledger","cas","provider"})
    void runtimeFailureTriggersRealSpringJdbcTransactionRollback(String stage) throws Exception {
        TransactionSynchronizationManager.clear();
        var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var manager=new DataSourceTransactionManager(source);var port=mock(Session.class);
        var work=new TransactionalSubmissionUnitOfWork(manager,()->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            if(stage.equals("provider")) throw failure(503,"DEPENDENCY_UNAVAILABLE");return port;
        });
        assertThrows(OperationFailure.class,()->work.execute(s->{
            assertSame(port,s);assertSame(connection,org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source));
            throw failure(stage.equals("audit")?500:409,stage.equals("audit")?"AUDIT_WRITE_FAILED":"VERSION_CONFLICT");
        }));
        verify(connection).rollback();verify(connection,never()).commit();verify(connection).close();
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        // Verifies Spring/Connection rollback, not MySQL data durability or simulated domain undo.
    }

    @Test void transactionReturnsOnlyAfterCommitAndUsesSameBoundConnection() throws Exception {
        TransactionSynchronizationManager.clear();var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var port=mock(Session.class);var work=new TransactionalSubmissionUnitOfWork(new DataSourceTransactionManager(source),()->port);
        assertEquals("ok",work.execute(s->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());assertSame(port,s);
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            assertSame(connection,org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source));return "ok";
        }));
        verify(connection).commit();verify(connection,never()).rollback();verify(connection).close();
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    @ParameterizedTest @ValueSource(strings={"repeatableRead","unknownIsolation","readOnly"})
    void requiredBoundaryRejectsIncompatibleOuterTransactionBeforeObtainingTheSession(String mode) throws Exception {
        TransactionSynchronizationManager.clear();var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var manager=new DataSourceTransactionManager(source);var outer=new TransactionTemplate(manager);
        if(mode.equals("repeatableRead")) outer.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        if(mode.equals("readOnly")) {
            outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);outer.setReadOnly(true);
        }
        @SuppressWarnings("unchecked") Supplier<Session> sessions=mock(Supplier.class);
        var work=new TransactionalSubmissionUnitOfWork(manager,sessions);
        error(503,"DEPENDENCY_UNAVAILABLE",()->outer.execute(status->work.execute(s->"must not mutate")));
        verifyNoInteractions(sessions);verify(connection).rollback();verify(connection,never()).commit();
    }

    @Test void requiredBoundaryJoinsCompatibleOuterTransactionWithOneConnectionAndOneCommit() throws Exception {
        TransactionSynchronizationManager.clear();var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var manager=new DataSourceTransactionManager(source);var outer=new TransactionTemplate(manager);
        outer.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var port=mock(Session.class);var work=new TransactionalSubmissionUnitOfWork(manager,()->port);
        assertEquals("joined",outer.execute(status->work.execute(s->{
            assertSame(connection,org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source));
            assertDoesNotThrow(()->verify(connection,never()).commit());return "joined";
        })));
        verify(source,times(1)).getConnection();verify(connection,times(1)).commit();verify(connection,never()).rollback();
    }

    @Test void simultaneousExpectedVersionCasAllowsOneWriterAndRollsBackTheOtherConnectionInTheUnitModel() throws Exception {
        TransactionSynchronizationManager.clear();
        var first=mock(Connection.class);var second=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(first,second);
        when(first.getAutoCommit()).thenReturn(true);when(second.getAutoCommit()).thenReturn(true);
        var version=new AtomicLong(2);var beforeCas=new CyclicBarrier(2);
        when(jdbc.update(startsWith("INSERT INTO internship_submissions"),any(),any(),any(),any(),any(),any(),any()))
                .thenAnswer(i->{beforeCas.await(5,TimeUnit.SECONDS);return 1;});
        when(jdbc.update(startsWith("UPDATE internship_task_assignments"),any(),any(),any()))
                .thenAnswer(i->version.compareAndSet(i.getArgument(3,Long.class),3)?1:0);
        var work=new TransactionalSubmissionUnitOfWork(new DataSourceTransactionManager(source),()->session);
        try(var executor=Executors.newFixedThreadPool(2)) {
            var outcomes=new ArrayList<Future<String>>();
            for(int i=0;i<2;i++) {
                var row=new SubmissionDto(id(10+i),ASSIGNMENT,1,List.of(),"https://example.test/v1",null,NOW,false);
                outcomes.add(executor.submit(()->{
                    try { work.execute(s->{s.appendFirst(context().assignment(),row);return null;});return "committed"; }
                    catch(OperationFailure conflict) {assertEquals(409,conflict.status);return conflict.code;}
                }));
            }
            var results=List.of(outcomes.get(0).get(10,TimeUnit.SECONDS),outcomes.get(1).get(10,TimeUnit.SECONDS));
            assertEquals(Set.of("committed","VERSION_CONFLICT"),new HashSet<>(results));assertEquals(3,version.get());
        }
        long commits=mockingDetails(first).getInvocations().stream().filter(i->i.getMethod().getName().equals("commit")).count()
                +mockingDetails(second).getInvocations().stream().filter(i->i.getMethod().getName().equals("commit")).count();
        long rollbacks=mockingDetails(first).getInvocations().stream().filter(i->i.getMethod().getName().equals("rollback")).count()
                +mockingDetails(second).getInvocations().stream().filter(i->i.getMethod().getName().equals("rollback")).count();
        assertEquals(1,commits);assertEquals(1,rollbacks);
        // Atomic CAS is a SQL test double. MySQL locking, UNIQUE and physical row rollback remain untested.
    }

    @Test void nullSessionProviderRollsBackInsteadOfInstallingFallback() throws Exception {
        TransactionSynchronizationManager.clear();var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var work=new TransactionalSubmissionUnitOfWork(new DataSourceTransactionManager(source),()->null);
        error(503,"DEPENDENCY_UNAVAILABLE",()->work.execute(s->"unreachable"));verify(connection).rollback();
    }

    @Test void springRegistersRealAdaptersButMissingBaselineStillBlocksTheHttpService() {
        TransactionSynchronizationManager.clear();
        try(var beans=new AnnotationConfigApplicationContext()) {
            // Prebuilt mocks are external owner dependencies, not beans needing JPA injection.
            // Production beans created by SubmissionConfiguration still use normal Spring wiring.
            beans.getBeanFactory().registerSingleton("jdbcTemplate",jdbc);
            beans.getBeanFactory().registerSingleton("operationLedger",ledger);
            beans.getBeanFactory().registerSingleton("auditService",audit);
            beans.getBeanFactory().registerSingleton("authorizationPolicy",core);
            beans.getBeanFactory().registerSingleton("workspaceAuthority",mock(WorkspaceAuthority.class));
            beans.getBeanFactory().registerSingleton("caseRuntime",mock(CaseRuntime.class));
            beans.registerBean(PlatformTransactionManager.class,TransactionalWorkspaceUnitOfWorkTest.Manager::new);
            beans.register(SubmissionConfiguration.class);beans.refresh();
            assertInstanceOf(JdbcSubmissionSession.class,beans.getBean(JdbcSubmissionSession.class));
            var service=beans.getBean(SubmissionService.class);
            error(503,"DEPENDENCY_UNAVAILABLE",()->service.submitUrlV1(actor,ASSIGNMENT,id(20).toString(),
                    new SubmissionInput(2L,List.of(),"https://example.test/v1",null)));
            verifyNoInteractions(core,ledger,audit);
        }
    }

    @ParameterizedTest @ValueSource(strings={"success","cas","audit","ledger"})
    void realServiceAndJdbcAdapterShareTheSpringTransactionAcrossEveryMutationBoundary(String stage) throws Exception {
        TransactionSynchronizationManager.clear();
        var connection=mock(Connection.class);var source=mock(DataSource.class);
        when(source.getConnection()).thenReturn(connection);when(connection.getAutoCommit()).thenReturn(true);
        var manager=new DataSourceTransactionManager(source);var cases=mock(CaseRuntime.class);
        var authority=mock(WorkspaceAuthority.class);
        ready();contextQuery();locks();insertSucceeds();
        when(core.isSelf(actor,STUDENT.toString())).thenReturn(true);
        when(core.holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"internship:student:submission_write")).thenReturn(true);
        when(cases.requireTaskParticipant(eq(actor),any(),any(),eq(CASE),any())).thenReturn(STUDENT);
        when(ledger.execute(any(),eq(SubmissionDto.class),any(),any())).thenAnswer(i->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertSame(connection,org.springframework.jdbc.datasource.DataSourceUtils.getConnection(source));
            var result=((Supplier<?>)i.getArgument(2)).get();
            if(stage.equals("ledger")) throw failure(503,"TRANSIENT_FAILURE"); // Simulates DONE persistence failure after effects.
            return result;
        });
        if(stage.equals("cas")) when(jdbc.update(startsWith("UPDATE internship_task_assignments"),any(),any(),any())).thenReturn(0);
        if(stage.equals("audit")) doThrow(failure(500,"AUDIT_WRITE_FAILED")).when(audit).workflow(anyString(),anyString(),anyString(),anyString(),anyMap());
        var service=new SubmissionConfiguration().submissionService(manager,session,core,authority,cases,new SubmissionConfiguration().submissionClock());
        Supplier<SubmissionDto> submit=()->service.submitUrlV1(actor,ASSIGNMENT,id(20).toString(),new SubmissionInput(2L,List.of(),"https://example.test/v1",null));
        if(stage.equals("success")) {
            var result=submit.get();assertEquals(1,result.versionNo());assertEquals(ASSIGNMENT,result.assignmentId());
            assertEquals(0,result.submittedAt().getNano()%1000);assertEquals("https://example.test/v1",result.url());
            verify(connection).commit();verify(connection,never()).rollback();
        } else {
            int status=stage.equals("audit")?500:stage.equals("ledger")?503:409;
            String code=stage.equals("audit")?"AUDIT_WRITE_FAILED":stage.equals("ledger")?"TRANSIENT_FAILURE":"VERSION_CONFLICT";
            error(status,code,()->submit.get());verify(connection).rollback();verify(connection,never()).commit();
        }
        verify(cases).requireTaskParticipant(eq(actor),any(),any(),eq(CASE),any());
        verify(core,times(2)).holdOrganization(actor,SCHOOL.toString(),Role.STUDENT,"internship:student:submission_write");
        if(stage.equals("cas")) verifyNoInteractions(audit);
        else verify(audit).workflow(eq(STUDENT.toString()),eq("SUBMISSION_CREATED"),anyString(),eq(SCHOOL.toString()),argThat(m->!m.containsKey("url")&&!m.containsKey("note")));
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private void baseline(List<List<String>> rows) {
        when(jdbc.query(startsWith("SELECT version,script"),any(RowMapper.class))).thenReturn(rows);
    }
    private void ready() { baseline(List.of(List.of("28","V28__private_file_service.sql"),List.of("29","V29__t3_submission_review_comment.sql"))); }
    private void locks() {
        when(jdbc.queryForList(eq("SELECT task_id FROM internship_task_assignments WHERE id=?"),eq(String.class),eq(ASSIGNMENT.toString()))).thenReturn(List.of(TASK.toString()));
        when(jdbc.queryForList(contains("internship_tasks WHERE id=? FOR SHARE"),eq(String.class),eq(TASK.toString()))).thenReturn(List.of(TASK.toString()));
        when(jdbc.queryForList(contains("WHERE id=? FOR UPDATE"),eq(String.class),eq(ASSIGNMENT.toString()))).thenReturn(List.of(TASK.toString()));
    }
    private void insertSucceeds() {
        when(jdbc.update(startsWith("UPDATE internship_task_assignments"),any(),any(),any())).thenReturn(1);
    }
    private void contextQuery() throws Exception {
        var row=mock(ResultSet.class);
        var strings=Map.ofEntries(Map.entry("case_id",CASE.toString()),Map.entry("student_user_id",STUDENT.toString()),Map.entry("state","IN_PROGRESS"),
                Map.entry("cohort_id",COHORT.toString()),Map.entry("enterprise_org_id",ENTERPRISE.toString()),Map.entry("school_org_id",SCHOOL.toString()),
                Map.entry("term_id",TERM.toString()),Map.entry("cohort_code","C1"),Map.entry("cohort_name","Cohort"),Map.entry("group_id",GROUP.toString()),
                Map.entry("group_code","G1"),Map.entry("task_id",TASK.toString()),Map.entry("title","Task"),Map.entry("description","Description"),Map.entry("priority","NORMAL"));
        when(row.getString(anyString())).thenAnswer(i->strings.get(i.getArgument(0)));
        when(row.getLong(anyString())).thenAnswer(i->"assignment_version".equals(i.getArgument(0))?2L:1L);
        when(row.getObject("due_at",LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC));
        when(row.getBoolean("late_allowed")).thenReturn(true);
        when(jdbc.query(contains("WHERE a.id=?"),any(RowMapper.class),eq(ASSIGNMENT.toString())))
                .thenAnswer(i->List.of(((RowMapper<?>)i.getArgument(1)).mapRow(row,0)));
    }
    private void artifactQuery(String mode) throws Exception {
        var row=mock(ResultSet.class);
        when(row.getString("id")).thenReturn(SUBMISSION.toString());when(row.getString("assignment_id")).thenReturn(ASSIGNMENT.toString());
        when(row.getString("url")).thenReturn(mode==null?"https://example.test/v1":null);when(row.getInt("version_no")).thenReturn(1);
        when(row.getObject("submitted_at",LocalDateTime.class)).thenReturn(LocalDateTime.ofInstant(NOW,ZoneOffset.UTC));
        when(jdbc.query(contains("WHERE id=?"),any(RowMapper.class),eq(SUBMISSION.toString())))
                .thenAnswer(i->List.of(((RowMapper<?>)i.getArgument(1)).mapRow(row,0)));
    }
    private static AssignmentContext context() {
        var cohort=new Cohort(COHORT,new WorkspaceScope(ENTERPRISE,SCHOOL,TERM),"C1","Cohort",1);
        var group=new InternshipGroup(GROUP,COHORT,"G1",List.of(),List.of(),1);
        var assignment=new AssignmentDto(ASSIGNMENT,CASE,STUDENT,AssignmentState.IN_PROGRESS,2L,null);
        var task=new InternshipTask(new TaskDto(TASK,GROUP,"Task","Description",NOW,TaskPriority.NORMAL,true,1L,List.of(assignment)));
        return new AssignmentContext(cohort,group,task,assignment);
    }
    private static SubmissionDto submission() { return new SubmissionDto(SUBMISSION,ASSIGNMENT,1,List.of(),"https://example.test/v1",null,NOW,false); }
    private static OperationLedger.Command command() { return new OperationLedger.Command(STUDENT.toString(),SCHOOL.toString(),"T3_SUBMISSION_CREATE",id(20).toString(),Map.of("fixture",true),201); }
    private static Authentication auth(UUID id) { return UsernamePasswordAuthenticationToken.authenticated(new AppPrincipal(id.toString(),"synthetic@example.test",null,1,List.of()),null,List.of()); }
    private static UUID id(int value) { return new UUID(0,value); }
    private static void error(int status,String code,Runnable operation) {
        var error=assertThrows(OperationFailure.class,operation::run);assertEquals(status,error.status);assertEquals(code,error.code);
    }
}
