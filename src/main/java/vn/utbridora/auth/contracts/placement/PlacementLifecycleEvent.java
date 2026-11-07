package vn.utbridora.auth.contracts.placement;

import java.time.Instant;
import java.util.regex.Pattern;
import vn.utbridora.auth.modules.internship.domain.PlacementEventEnvelopePolicy;

/**
 * Canonical lifecycle payload from Contract Pack v1.0, C.0/BC41.
 *
 * <p>Construction validates values and throws {@link IllegalArgumentException}
 * with no input values in its message. An adapter must reject malformed wire
 * data (including missing nullable fields and unknown fields) before delivery.
 * Error-to-delivery-result mapping belongs to that adapter; no event ID is invented.
 * This payload is neither authoritative Placement state nor proof of approval.
 */
public record PlacementLifecycleEvent(
        String eventId,
        String eventType,
        Integer schemaVersion,
        String placementId,
        Long aggregateVersion,
        Long placementVersion,
        String studentUserId,
        String schoolOrgId,
        String enterpriseOrgId,
        String termId,
        Instant effectiveAt,
        Instant occurredAt,
        String traceId,
        String reasonCode,
        String decisionRef) {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern REASON_PATTERN = Pattern.compile("[A-Z0-9_]{3,64}");

    public PlacementLifecycleEvent {
        requireUuid(eventId, "eventId");
        requireUuid(placementId, "placementId");
        requireUuid(studentUserId, "studentUserId");
        requireUuid(schoolOrgId, "schoolOrgId");
        requireUuid(enterpriseOrgId, "enterpriseOrgId");
        requireUuid(termId, "termId");
        requireUuid(traceId, "traceId");
        if (!PlacementEventEnvelopePolicy.hasSupportedTypeAndVersions(
                eventType, schemaVersion, aggregateVersion, placementVersion)) {
            throw new IllegalArgumentException("Invalid lifecycle event type, schema or versions");
        }
        if (effectiveAt == null || occurredAt == null) {
            throw new IllegalArgumentException("effectiveAt and occurredAt are required");
        }
        if (reasonCode != null && !REASON_PATTERN.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("Invalid reasonCode");
        }
        if (decisionRef != null) {
            decisionRef = trimUnicode(decisionRef);
            int length = decisionRef.codePointCount(0, decisionRef.length());
            if (length < 1 || length > 120) {
                throw new IllegalArgumentException("Invalid decisionRef");
            }
        }
    }

    // Shared only within this canonical package; no additional public contract type.
    static void requireUuid(String value, String field) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a UUID");
        }
    }

    private static String trimUnicode(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isSpace(value.codePointAt(start))) {
            start += Character.charCount(value.codePointAt(start));
        }
        while (end > start && isSpace(value.codePointBefore(end))) {
            end -= Character.charCount(value.codePointBefore(end));
        }
        return value.substring(start, end);
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
