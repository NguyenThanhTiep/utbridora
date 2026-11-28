package vn.utbridora.auth.modules.cooperation.api;

import java.time.Instant;
import java.util.List;
import vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidationResult.CodeKind;
import vn.utbridora.auth.modules.cooperation.domain.VietnameseBusinessTextNormalizer.Match;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort.TaxpayerStatus;

/** Additive sidecar; CP-T5UI1 VerificationInput and VerificationView remain frozen. */
public record TaxAssessmentView(String rawRegistrationCode, String normalizedTaxCode, CodeKind codeKind, Boolean checksumValid,
    LookupStatus lookupStatus, TaxpayerStatus taxpayerStatus, String taxpayerStatusLabel, String officialName, String officialAddress,
    String taxDepartment, String provider, Instant checkedAt, Verdict verdict, Match nameMatch, Match addressMatch,
    List<String> mismatchedFields, List<String> reasons) {
    public enum LookupStatus { FOUND, NOT_FOUND, UNAVAILABLE, SKIPPED }
    public enum Verdict { INACTIVE_OR_INVALID, INFO_MISMATCH, MATCHED_ACTIVE, UNVERIFIED_OFFLINE }
}
