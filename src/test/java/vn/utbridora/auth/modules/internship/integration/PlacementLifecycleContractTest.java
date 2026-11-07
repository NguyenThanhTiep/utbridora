package vn.utbridora.auth.modules.internship.integration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.utbridora.auth.contracts.placement.EventConsumeResult;
import vn.utbridora.auth.contracts.placement.PlacementLifecycleEvent;
import vn.utbridora.auth.contracts.placement.TrustedEventContext;
import vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicy;

import static org.junit.jupiter.api.Assertions.*;

/** Value/signature tests only: no consumer, Case persistence or Task service is exercised. */
class PlacementLifecycleContractTest {
    private static final long MAX_VERSION = 9_007_199_254_740_991L;
    private static final String EVENT_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";
    private static final String REQUEST_ID = "00000000-0000-4000-8000-000000000099";
    private static final Instant EFFECTIVE_AT = Instant.parse("2026-11-03T00:00:00Z");

    // Values copied from the approved BC41 v1.0 event example, not v1.1 schemas.
    private static PlacementLifecycleEvent event() {
        return new PlacementLifecycleEvent(
                EVENT_ID, "PlacementActivated", 1, "99999999-9999-4999-8999-999999999999", 3L, 3L,
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333", "44444444-4444-8444-4444-444444444444",
                EFFECTIVE_AT, Instant.parse("2026-11-03T00:00:01Z"), REQUEST_ID, null, null);
    }

    private static TrustedEventContext context() {
        return new TrustedEventContext("T5", "00000000-0000-4000-8000-000000000098", REQUEST_ID);
    }

    private static EventConsumeResult result() {
        return new EventConsumeResult(EVENT_ID, "ACK_APPLIED", 3L, "APPLIED", null);
    }

    @Test
    void canonicalShapesMatchV1WithoutAdditionalStateOrOtherComponents() {
        assertShape(PlacementLifecycleEvent.class, List.of(
                "eventId:String", "eventType:String", "schemaVersion:Integer", "placementId:String",
                "aggregateVersion:Long", "placementVersion:Long", "studentUserId:String",
                "schoolOrgId:String", "enterpriseOrgId:String", "termId:String", "effectiveAt:Instant",
                "occurredAt:Instant", "traceId:String", "reasonCode:String", "decisionRef:String"));
        assertShape(TrustedEventContext.class,
                List.of("producerModule:String", "deliveryOperationId:String", "requestId:String"));
        assertShape(EventConsumeResult.class, List.of("eventId:String", "outcome:String",
                "appliedAggregateVersion:Long", "reasonCode:String", "retryAfterSeconds:Integer"));
    }

    private static void assertShape(Class<? extends Record> type, List<String> expected) {
        assertEquals("vn.utbridora.auth.contracts.placement", type.getPackageName());
        assertEquals(expected, Arrays.stream(type.getRecordComponents())
                .map(c -> c.getName() + ":" + c.getType().getSimpleName()).toList());
    }

    @Test
    void passesTheBc41FixtureThroughThePortSignatureOnly() throws Exception {
        var method = PlacementLifecycleConsumer.class.getMethod(
                "consume", TrustedEventContext.class, PlacementLifecycleEvent.class);
        assertEquals(EventConsumeResult.class, method.getReturnType());
        assertEquals(1, PlacementLifecycleConsumer.class.getDeclaredMethods().length);
        var receivedContext = new AtomicReference<TrustedEventContext>();
        var receivedEvent = new AtomicReference<PlacementLifecycleEvent>();
        // This return value is a fixture for transport only, not evidence of a committed Case.
        var fixtureResult = result();
        PlacementLifecycleConsumer port = (context, event) -> {
            receivedContext.set(context);
            receivedEvent.set(event);
            return fixtureResult;
        };
        var fixtureEvent = event();
        var fixtureContext = context();
        assertSame(fixtureResult, port.consume(fixtureContext, fixtureEvent));
        assertSame(fixtureEvent, receivedEvent.get());
        assertSame(fixtureContext, receivedContext.get());
        assertEquals(EFFECTIVE_AT, receivedEvent.get().effectiveAt());
        assertEquals(EVENT_ID, fixtureResult.eventId());
        assertNull(fixtureEvent.reasonCode());
        assertNull(fixtureEvent.decisionRef());
    }

    static Stream<Arguments> requiredFields() {
        return Stream.concat(Stream.concat(
                Stream.of("eventId", "eventType", "schemaVersion", "placementId", "aggregateVersion",
                        "placementVersion", "studentUserId", "schoolOrgId", "enterpriseOrgId", "termId",
                        "effectiveAt", "occurredAt", "traceId").map(f -> Arguments.of(event(), f)),
                Stream.of("producerModule", "deliveryOperationId", "requestId")
                        .map(f -> Arguments.of(context(), f))),
                Stream.of("eventId", "outcome", "reasonCode").map(f -> Arguments.of(result(), f)));
    }

    @ParameterizedTest
    @MethodSource("requiredFields")
    void constructorRejectsEveryRequiredNullWithoutAValidationFramework(Record fixture, String field) {
        assertThrows(IllegalArgumentException.class, () -> with(fixture, field, null));
    }

    static Stream<Arguments> malformedIds() {
        Stream<Arguments> fields = Stream.concat(Stream.concat(
                Stream.of("eventId", "placementId", "studentUserId", "schoolOrgId", "enterpriseOrgId",
                        "termId", "traceId").map(f -> Arguments.of(event(), f)),
                Stream.of("deliveryOperationId", "requestId").map(f -> Arguments.of(context(), f))),
                Stream.of(Arguments.of(result(), "eventId")));
        return fields.flatMap(a -> Stream.of("", " ", "1-1-1-1-1", "z".repeat(36),
                        " " + EVENT_ID, EVENT_ID + " ", EVENT_ID.replace("-", ""))
                .map(value -> Arguments.of(a.get()[0], a.get()[1], value)));
    }

    @ParameterizedTest
    @MethodSource("malformedIds")
    void rejectsMalformedIdsWithoutEchoingTheirValues(Record fixture, String field, String value) {
        var error = assertThrows(IllegalArgumentException.class, () -> with(fixture, field, value));
        assertEquals(field + " must be a UUID", error.getMessage());
    }

    @Test
    void acceptsUppercaseUuidWithoutChangingItsValue() throws Exception {
        String uppercase = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE";
        assertEquals(uppercase, with(event(), "eventId", uppercase).eventId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PlacementApproved", "PlacementActivated", "PlacementCancelled", "PlacementCompleted"})
    void acceptsExactlyTheFourLifecycleTypesAtVersionBoundaries(String type) {
        for (long version : new long[]{0, 1, MAX_VERSION}) {
            assertDoesNotThrow(() -> new PlacementLifecycleEvent(EVENT_ID, type, 1, event().placementId(),
                    version, version, event().studentUserId(), event().schoolOrgId(), event().enterpriseOrgId(),
                    event().termId(), EFFECTIVE_AT, event().occurredAt(), REQUEST_ID, null, null));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CaseClosed", "UNKNOWN", "placementActivated", " PlacementActivated", "PlacementActivated "})
    void rejectsInvalidEventTypes(String type) {
        assertThrows(IllegalArgumentException.class, () -> with(event(), "eventType", type));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 2, Integer.MAX_VALUE})
    void rejectsSchemaVersionsOtherThanOne(Integer version) {
        assertThrows(IllegalArgumentException.class, () -> with(event(), "schemaVersion", version));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {Long.MIN_VALUE, -1, 9_007_199_254_740_992L, Long.MAX_VALUE})
    void rejectsInvalidVersionOnEitherSide(Long version) {
        assertThrows(IllegalArgumentException.class, () -> with(event(), "aggregateVersion", version));
        assertThrows(IllegalArgumentException.class, () -> with(event(), "placementVersion", version));
    }

    @Test
    void unequalVersionsFailBeforePortDeliveryWithoutFabricatingAResult() {
        var called = new AtomicBoolean();
        PlacementLifecycleConsumer port = (context, event) -> {
            called.set(true);
            return result();
        };
        assertThrows(IllegalArgumentException.class,
                () -> port.consume(context(), with(event(), "placementVersion", 4L)));
        assertFalse(called.get());
        // The adapter's future REJECT/INVALID_SCHEMA mapping is not implemented here.
    }

    @Test
    void malformedEventIdFailsBeforeDeliveryWithoutGeneratingASubstituteId() {
        var called = new AtomicBoolean();
        PlacementLifecycleConsumer port = (context, event) -> {
            called.set(true);
            return result();
        };
        assertThrows(IllegalArgumentException.class,
                () -> port.consume(context(), with(event(), "eventId", "invalid-private-input")));
        assertFalse(called.get());
    }

    @Test
    void optionalReasonChecksContractLengthAndAlphabet() throws Exception {
        assertNull(with(event(), "reasonCode", null).reasonCode());
        for (String value : List.of("ABC", "A_1", "A".repeat(64))) {
            assertEquals(value, with(event(), "reasonCode", value).reasonCode());
        }
        for (String value : List.of("", "AB", "A".repeat(65), "abc", "ABC-DEF", " ABC", "ABC\n")) {
            var error = assertThrows(IllegalArgumentException.class, () -> with(event(), "reasonCode", value));
            assertEquals("Invalid reasonCode", error.getMessage());
        }
    }

    @Test
    void decisionReferenceUsesUnicodeTrimAndCodePointLength() throws Exception {
        assertNull(with(event(), "decisionRef", null).decisionRef());
        assertEquals("Q\u0110 01", with(event(), "decisionRef", "\u00a0\u2003Q\u0110 01\u3000\n").decisionRef());
        String supplementary = "\ud83d\udcc4".repeat(120);
        assertEquals(supplementary, with(event(), "decisionRef", supplementary).decisionRef());
        assertEquals("A", with(event(), "decisionRef", "A").decisionRef());
        for (String value : List.of("", " \t\n", "\u00a0\u2003\u3000", "A".repeat(121), supplementary + "X")) {
            var error = assertThrows(IllegalArgumentException.class, () -> with(event(), "decisionRef", value));
            assertEquals("Invalid decisionRef", error.getMessage());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"T3", "T5"})
    void sharedContextAllowsBothBc41ProducersButDoesNotEstablishTrust(String producer) throws Exception {
        assertEquals(producer, with(context(), "producerModule", producer).producerModule());
        // T3 is needed by CaseClosed consumers; a future T3 Placement adapter must require verified T5.
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"T1", "T2", "T4", "t5", " T5", "T5 ", "BROWSER"})
    void rejectsUnknownOrNonExactProducers(String producer) {
        assertThrows(IllegalArgumentException.class, () -> with(context(), "producerModule", producer));
    }

    @ParameterizedTest
    @CsvSource({
        "ACK_APPLIED,APPLIED", "ACK_DUPLICATE,DUPLICATE", "ACK_STALE,STALE",
        "RETRY,SOURCE_UNAVAILABLE", "REJECT,SOURCE_NOT_FOUND", "REJECT,SCOPE_MISMATCH",
        "REJECT,INVALID_SCHEMA", "REJECT,STATE_CONFLICT"
    })
    void acceptsBc41OutcomeAndReasonValues(String outcome, String reason) {
        assertDoesNotThrow(() -> new EventConsumeResult(EVENT_ID, outcome, null, reason, null));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ACK", "SUCCESS", "ack_applied", " ACK_APPLIED", "ACK_APPLIED "})
    void rejectsUnsupportedOutcomes(String outcome) {
        assertThrows(IllegalArgumentException.class, () -> with(result(), "outcome", outcome));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"INVALID_REQUEST", "APPLIED_EXTRA", "applied", " APPLIED", "APPLIED "})
    void rejectsUnsupportedResultReasons(String reason) {
        assertThrows(IllegalArgumentException.class, () -> with(result(), "reasonCode", reason));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, 1, 9_007_199_254_740_991L})
    void resultVersionIsNullableAndBounded(Long version) throws Exception {
        assertEquals(version, with(result(), "appliedAggregateVersion", version).appliedAggregateVersion());
    }

    @ParameterizedTest
    @ValueSource(longs = {Long.MIN_VALUE, -1, 9_007_199_254_740_992L, Long.MAX_VALUE})
    void rejectsOutOfRangeResultVersion(long version) {
        assertThrows(IllegalArgumentException.class, () -> with(result(), "appliedAggregateVersion", version));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {Integer.MIN_VALUE, -1, 0, 1, 1800, Integer.MAX_VALUE})
    void preservesV1NullableIntWithoutImportingCandidateRetryConstraints(Integer seconds) throws Exception {
        assertEquals(seconds, with(result(), "retryAfterSeconds", seconds).retryAfterSeconds());
    }

    @ParameterizedTest
    @CsvSource({
        "IN_PROGRESS,APPROVED,0,false", "IN_PROGRESS,PENDING_APPROVAL,0,false",
        "IN_PROGRESS,CANCELLED,0,false", "COMPLETED,ACTIVE,0,false",
        "IN_PROGRESS,ACTIVE,1,false", "IN_PROGRESS,ACTIVE,0,true", "IN_PROGRESS,ACTIVE,-1,true"
    })
    void activatedEventNeverReplacesIndependentAuthoritativeStateAndTimeChecks(
            String caseState, String sourceState, long offsetNanos, boolean expected) throws Exception {
        var payload = with(event(), "effectiveAt", EFFECTIVE_AT.plusNanos(offsetNanos));
        assertEquals("PlacementActivated", payload.eventType());
        // Direct policy inputs only; this is not evidence of a T5 read or a real Task mutation guard.
        assertEquals(expected, InternshipActivityPolicy.hasRequiredStateAndStartTime(
                caseState, sourceState, payload.effectiveAt(), EFFECTIVE_AT));
    }

    @Test
    void eventTimestampsDoNotInventChronologicalOrCurrentTimeConstraints() throws Exception {
        var payload = with(event(), "effectiveAt", EFFECTIVE_AT.plusSeconds(3600));
        assertTrue(payload.effectiveAt().isAfter(payload.occurredAt()));
        assertFalse(InternshipActivityPolicy.hasRequiredStateAndStartTime(
                "IN_PROGRESS", "ACTIVE", payload.effectiveAt(), EFFECTIVE_AT));
    }

    /** Copy a fixture with one changed component, invoking its real canonical constructor. */
    private static <T extends Record> T with(T fixture, String field, Object value) throws Exception {
        RecordComponent[] components = fixture.getClass().getRecordComponents();
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        Object[] values = new Object[components.length];
        boolean found = false;
        for (int i = 0; i < components.length; i++) {
            boolean replace = components[i].getName().equals(field);
            values[i] = replace ? value : components[i].getAccessor().invoke(fixture);
            found |= replace;
        }
        assertTrue(found, "Unknown fixture component: " + field);
        try {
            @SuppressWarnings("unchecked")
            T copy = (T) fixture.getClass().getDeclaredConstructor(types).newInstance(values);
            return copy;
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw exception;
        }
    }
}
