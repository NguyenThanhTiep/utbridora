package vn.utbridora.auth.modules.internship.api;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.internship.api.SubmissionDtos.*;

class SubmissionDtosTest {
    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000031");
    private static final Instant NOW = Instant.parse("2026-11-10T03:00:00Z");
    private static final long MAX = 9_007_199_254_740_991L;

    private static SubmissionInput input() { return new SubmissionInput(2L, List.of(), "https://example.test/v1", null); }
    private static SubmissionDto output() { return new SubmissionDto(ID, ID, 1, List.of(ID), null, null, NOW, false); }
    private static ReviewInput review() { return new ReviewInput(3L, ReviewVerdict.REQUEST_REVISION, "Please revise"); }
    private static ReviewDto reviewed() { return new ReviewDto(ID, ID, 1, ReviewVerdict.ACCEPT, "Accepted", ID, NOW, 4L); }
    private static CommentDto commented() { return new CommentDto(ID, ID, ID, "Comment", NOW); }

    @Test void shapesExactlyMatchCpWs2WithoutExtraClientStateOrActor() {
        shape(SubmissionInput.class, "expectedAssignmentVersion:Long,fileIds:List,url:String,note:String");
        shape(SubmissionDto.class, "submissionId:UUID,assignmentId:UUID,versionNo:Integer,fileIds:List,url:String,note:String,submittedAt:Instant,lateFlag:Boolean");
        shape(ReviewInput.class, "expectedAssignmentVersion:Long,verdict:ReviewVerdict,comment:String");
        shape(ReviewDto.class, "reviewId:UUID,submissionId:UUID,reviewedVersionNo:Integer,verdict:ReviewVerdict,comment:String,reviewerUserId:UUID,createdAt:Instant,assignmentVersion:Long");
        shape(CommentInput.class, "text:String");
        shape(CommentDto.class, "commentId:UUID,assignmentId:UUID,authorUserId:UUID,text:String,createdAt:Instant");
        assertEquals(List.of("ACCEPT", "REQUEST_REVISION"), Arrays.stream(ReviewVerdict.values()).map(Enum::name).toList());
    }

    private static void shape(Class<? extends Record> type, String expected) {
        assertEquals(List.of(expected.split(",")), Arrays.stream(type.getRecordComponents())
                .map(c -> c.getName() + ":" + c.getType().getSimpleName()).toList());
    }

    static Stream<Arguments> requiredFields() {
        return Stream.of(input(), output(), review(), reviewed(), new CommentInput("Comment"), commented())
                .flatMap(record -> Arrays.stream(record.getClass().getRecordComponents())
                        .filter(c -> !List.of("url", "note").contains(c.getName()))
                        .map(c -> Arguments.of(record, c.getName())));
    }

    @ParameterizedTest @MethodSource("requiredFields")
    void everyRequiredComponentRejectsNullAtConstruction(Record fixture, String field) {
        assertThrows(IllegalArgumentException.class, () -> copy(fixture, field, null));
    }

    @ParameterizedTest @ValueSource(longs = {0, 1, 9_007_199_254_740_991L})
    void usesTheExistingContractVersionRangeIncludingZeroAndMax(long version) throws Exception {
        assertEquals(version, copy(input(), "expectedAssignmentVersion", version).expectedAssignmentVersion());
        assertEquals(version, copy(review(), "expectedAssignmentVersion", version).expectedAssignmentVersion());
        assertEquals(version, copy(reviewed(), "assignmentVersion", version).assignmentVersion());
    }

    @ParameterizedTest @ValueSource(longs = {Long.MIN_VALUE, -1, 9_007_199_254_740_992L, Long.MAX_VALUE})
    void rejectsVersionsOutsideContractV(long version) {
        assertThrows(IllegalArgumentException.class, () -> copy(input(), "expectedAssignmentVersion", version));
        assertThrows(IllegalArgumentException.class, () -> copy(review(), "expectedAssignmentVersion", version));
        assertThrows(IllegalArgumentException.class, () -> copy(reviewed(), "assignmentVersion", version));
    }

    @ParameterizedTest @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
    void submissionAndReviewedSequenceNumbersMustBePositive(int version) {
        assertThrows(IllegalArgumentException.class, () -> copy(output(), "versionNo", version));
        assertThrows(IllegalArgumentException.class, () -> copy(reviewed(), "reviewedVersionNo", version));
    }

    @Test void noteIsNullableOrEmptyAndTrimsUnicodeByCodePoint() throws Exception {
        assertNull(input().note());
        assertNull(output().note());
        assertEquals("", copy(input(), "note", "\u00a0 \u3000").note());
        assertEquals("Note", copy(output(), "note", "\u00a0Note\u3000").note());
        String max = "\ud83d\udcc4".repeat(2_000);
        assertEquals(max, copy(input(), "note", max).note());
        assertEquals(max, copy(output(), "note", max).note());
        assertThrows(IllegalArgumentException.class, () -> copy(input(), "note", max + "X"));
        assertThrows(IllegalArgumentException.class, () -> copy(output(), "note", max + "X"));
    }

    @Test void reviewAndCommentTextsUseTheExistingUnicodeTextConvention() throws Exception {
        String max = "\ud83d\udcc4".repeat(2_000);
        assertEquals(max, new CommentInput(max).text());
        assertEquals("Text", new CommentInput("\u00a0 Text \u3000").text());
        assertEquals(max, copy(review(), "comment", max).comment());
        assertEquals(max, copy(reviewed(), "comment", max).comment());
        assertEquals(max, copy(commented(), "text", max).text());
        for (String invalid : List.of("", " \t", "\u00a0\u3000", max + "X")) {
            assertThrows(IllegalArgumentException.class, () -> new CommentInput(invalid));
            assertThrows(IllegalArgumentException.class, () -> copy(review(), "comment", invalid));
            assertThrows(IllegalArgumentException.class, () -> copy(reviewed(), "comment", invalid));
            assertThrows(IllegalArgumentException.class, () -> copy(commented(), "text", invalid));
        }
        // ACCEPT also requires comment under v1.0, not only REQUEST_REVISION.
        assertThrows(IllegalArgumentException.class, () -> new ReviewInput(1L, ReviewVerdict.ACCEPT, ""));
    }

    @Test void fileOrderAndPreviouslyConstructedVersionsRemainImmutable() {
        UUID second = new UUID(0, 42);
        var files = new ArrayList<>(List.of(ID, second));
        var first = new SubmissionDto(ID, ID, 1, files, null, "v1", NOW, false);
        var request = new SubmissionInput(2L, files, null, null);
        files.clear();
        var next = new SubmissionDto(second, ID, 2, List.of(), "https://example.test/v2", "v2", NOW, true);
        assertEquals(List.of(ID, second), first.fileIds());
        assertEquals(List.of(ID, second), request.fileIds());
        assertThrows(UnsupportedOperationException.class, () -> first.fileIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.fileIds().clear());
        assertEquals("v1", first.note());
        assertEquals(2, next.versionNo());
        // Value immutability only; database history belongs to M2 integration tests.
    }

    @Test void responseValuesSerializeWithWireEnumAndNullability() {
        var json = JsonMapper.builder().build();
        assertEquals(output(), json.readValue(json.writeValueAsString(output()), SubmissionDto.class));
        assertEquals(reviewed(), json.readValue(json.writeValueAsString(reviewed()), ReviewDto.class));
        assertEquals(commented(), json.readValue(json.writeValueAsString(commented()), CommentDto.class));
        assertTrue(json.readTree(json.writeValueAsString(output())).get("lateFlag").isBoolean());
        assertEquals("ACCEPT", json.readTree(json.writeValueAsString(reviewed())).get("verdict").asString());
    }

    private static <T extends Record> T copy(T fixture, String field, Object value) throws Exception {
        var components = fixture.getClass().getRecordComponents();
        var types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            values[i] = components[i].getName().equals(field) ? value : components[i].getAccessor().invoke(fixture);
        }
        try {
            @SuppressWarnings("unchecked") T result = (T) fixture.getClass().getDeclaredConstructor(types).newInstance(values);
            return result;
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) throw cause;
            throw failure;
        }
    }
}
