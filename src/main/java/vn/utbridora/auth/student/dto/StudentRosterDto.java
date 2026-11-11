
package vn.utbridora.auth.student.dto;

import java.time.Instant;

public record StudentRosterDto(
    String id,
    String schoolOrgId,
    String studentCode,
    String userId,
    String fullName,
    String email,
    String majorCode,
    String termCode,
    Instant createdAt
) {}
