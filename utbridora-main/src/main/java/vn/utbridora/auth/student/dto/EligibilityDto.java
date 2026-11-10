
package vn.utbridora.auth.student.dto;

import java.time.Instant;

public record EligibilityDto(
    String id,
    String studentRosterId,
    String termCode,
    boolean schoolApproved,
    Instant createdAt
) {}
