package vn.utbridora.auth.modules.cooperation.port;

/**
 * Read port for School-Enterprise Verification status.
 * Used by T3 (confirm coordinator) and T5 (submit Cooperation, create Placement).
 *
 * Callers must not bypass this port and read the table directly.
 * Deny by default when verification is missing or not APPROVED.
 */
public interface OrganizationSchoolVerificationPort {

    /**
     * Returns the current verification view for the given enterprise+school pair.
     * Never throws; returns {@code schoolApproved=false} when absent or not APPROVED.
     */
    SchoolVerificationView get(String enterpriseOrgId, String schoolOrgId);

    record SchoolVerificationView(
            String enterpriseOrgId,
            String schoolOrgId,
            String verificationId,      // null when no record exists
            String status,              // null when no record exists
            Long version,               // null when no record exists
            boolean schoolApproved      // true only when state == APPROVED
    ) {}
}
