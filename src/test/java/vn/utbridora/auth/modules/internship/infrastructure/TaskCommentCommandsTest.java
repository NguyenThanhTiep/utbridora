package vn.utbridora.auth.modules.internship.infrastructure;

import java.util.*;
import java.util.function.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;
import vn.utbridora.auth.modules.internship.application.*;
import vn.utbridora.auth.operations.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskCommentCommandsTest extends WeeklyReportAdapterTestSupport {
    private final TaskCommentService comments = mock(TaskCommentService.class);
    private final OperationLedger ledger = mock(OperationLedger.class);
    private final Authentication actor = auth(student);
    private final UUID assignmentId = UUID.randomUUID(), key = UUID.randomUUID();
    private final CommentInput input = new CommentInput("Message");
    private final CommentDto dto = new CommentDto(UUID.randomUUID(), assignmentId, student, input.text(), now);
    private TaskCommentCommands commands;
    @BeforeEach void setup() {
        commands = new TaskCommentCommands(new TransactionalWorkspaceUnitOfWork(manager, () -> mock(WorkspacePorts.Session.class)), comments, ledger);
        when(comments.authorizeCreate(actor, assignmentId)).thenReturn(new TaskCommentService.MutationScope(student, school));
    }
    @Test void canonicalParserAndExistingLedgerCreateAnAppendAfterCurrentAuthorization() {
        when(comments.create(actor, assignmentId, input)).thenReturn(dto);
        when(ledger.execute(any(), eq(CommentDto.class), any(), any())).thenAnswer(call -> {
            Supplier<CommentDto> effect = call.getArgument(2); var result = effect.get();
            Function<CommentDto, String> ref = call.getArgument(3); assertEquals(dto.commentId().toString(), ref.apply(result));
            return result;
        });
        assertSame(dto, commands.create(actor, assignmentId, key, "{\"text\":\" Message \"}"));
        var order = inOrder(comments, ledger);
        order.verify(comments).authorizeCreate(actor, assignmentId);
        var command = ArgumentCaptor.forClass(OperationLedger.Command.class);
        order.verify(ledger).execute(command.capture(), eq(CommentDto.class), any(), any());
        order.verify(comments).create(actor, assignmentId, input);
        assertEquals("T3_TASK_COMMENT_CREATE", command.getValue().operationCode()); assertEquals(201, command.getValue().responseStatus());
        assertEquals(school.toString(), command.getValue().organizationId());
        assertEquals(Map.of("assignmentId", assignmentId, "request", input), command.getValue().payload());
        assertEquals(1, manager.commits);
    }
    @Test void replayChecksCurrentWriterAndDoesNotAppendAgain() {
        when(ledger.execute(any(), eq(CommentDto.class), any(), any())).thenReturn(dto);
        assertSame(dto, commands.create(actor, assignmentId, key, input));
        verify(comments).authorizeCreate(actor, assignmentId); verify(comments, never()).create(any(), any(), any());
    }
    @Test void revokedWriterCannotReadCachedCommentText() {
        when(comments.authorizeCreate(actor, assignmentId)).thenThrow(new OperationFailure(404, "RESOURCE_NOT_FOUND", "Denied", null));
        code(404, "RESOURCE_NOT_FOUND", () -> commands.create(actor, assignmentId, key, input));
        verifyNoInteractions(ledger); assertEquals(1, manager.rollbacks);
    }
    @Test void missingKeyReadOnlyTransactionAndForgedBodyCannotReachLedger() {
        code(400, "INVALID_REQUEST", () -> commands.create(actor, assignmentId, null, input));
        code(503, "DEPENDENCY_UNAVAILABLE", () -> inReadOnlyTransaction(() -> commands.create(actor, assignmentId, key, input)));
        code(400, "SCHEMA_VALIDATION", () -> commands.create(actor, assignmentId, key, "{\"text\":\"Message\",\"authorUserId\":\"" + mentor + "\"}"));
        code(400, "SCHEMA_VALIDATION", () -> commands.create(actor, assignmentId, key, "{\"text\":\"Message\",\"verdict\":\"ACCEPT\"}"));
        verifyNoInteractions(comments, ledger);
    }
    @Test void wrongAssignmentAuthorOrTextInResponseFailsBeforeCommit() {
        for (var wrong : List.of(new CommentDto(dto.commentId(), UUID.randomUUID(), student, input.text(), now),
                new CommentDto(dto.commentId(), assignmentId, mentor, input.text(), now),
                new CommentDto(dto.commentId(), assignmentId, student, "Different text", now))) {
            when(ledger.execute(any(), eq(CommentDto.class), any(), any())).thenReturn(wrong);
            code(503, "DEPENDENCY_UNAVAILABLE", () -> commands.create(actor, assignmentId, key, input));
        }
        assertEquals(0, manager.commits); assertEquals(3, manager.rollbacks);
    }
}
