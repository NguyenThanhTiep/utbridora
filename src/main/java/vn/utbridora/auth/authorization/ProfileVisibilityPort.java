package vn.utbridora.auth.authorization;

/** Integration contract: T2/T5 must supply real ownership/sharing/consent decisions.
 * Deny by default until a reviewed adapter replaces this interface's implementation.
 */
public interface ProfileVisibilityPort {
    boolean explicitlySharedWithEnterprise(String studentId,String enterpriseOrganizationId);
    boolean delegatedToSchool(String studentId,String universityOrganizationId);
}
