package vn.utbridora.auth.modules.cooperation.domain;

public record TaxCodeValidationResult(String normalizedTaxCode, CodeKind codeKind, Boolean checksumValid, String message) {
    public enum CodeKind { ENTERPRISE_10, BRANCH_13, PERSONAL_ID_12, INVALID_FORMAT }
    public boolean validEnterprise() {
        return (codeKind == CodeKind.ENTERPRISE_10 || codeKind == CodeKind.BRANCH_13) && Boolean.TRUE.equals(checksumValid);
    }
}
