package vn.utbridora.auth.authorization;

/** Safe default profile adapter. Replace with a reviewed T2/T5 adapter in the business module. */
public final class DenyAllProfileVisibility implements ProfileVisibilityPort {
    @Override public boolean explicitlySharedWithEnterprise(String studentId,String enterpriseOrgId) {return false;}
    @Override public boolean delegatedToSchool(String studentId,String universityOrgId) {return false;}
}
