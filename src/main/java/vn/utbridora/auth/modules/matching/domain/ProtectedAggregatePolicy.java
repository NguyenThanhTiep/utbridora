package vn.utbridora.auth.modules.matching.domain;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;
import vn.utbridora.auth.operations.WorkflowValues;

/** Quy tắc bảo vệ thống kê doanh nghiệp, mỗi đợt demo chỉ phát hành một lần. */
public final class ProtectedAggregatePolicy {
    private ProtectedAggregatePolicy() {}

    /** DTO thống kê duy nhất dành cho JSON, CSV và biểu đồ của doanh nghiệp. */
    public record ProtectedAggregate(String releaseId, String matchRunId, String eligibleBand) {}

    /** Số lượng dưới năm được biểu diễn bằng một nhãn không chứa số đếm. */
    public static String eligibleBand(long eligibleCount) {
        if (eligibleCount < 0) throw new IllegalArgumentException("eligibleCount must not be negative");
        if (eligibleCount < 5) return "SUPPRESSED";
        if (eligibleCount < 10) return "5-9";
        if (eligibleCount < 20) return "10-19";
        return "20+";
    }

    /**
     * Băm định danh release theo SRS §25.8.2. Không đưa actor, demand, bộ lọc hay khóa yêu cầu
     * vào fingerprint để việc thay đổi chúng không tạo được release thứ hai trong cùng phạm vi.
     */
    public static String fingerprint(ReleaseIdentity identity) {
        var canonical = new TreeMap<String, String>();
        canonical.put("criteriaHash", identity.criteriaHash());
        canonical.put("datasetSnapshotId", identity.datasetSnapshotId());
        canonical.put("enterpriseOrgId", identity.enterpriseOrgId());
        canonical.put("profileSnapshotVersion", identity.profileSnapshotVersion());
        canonical.put("ruleVersion", identity.ruleVersion());
        canonical.put("schoolOrgId", identity.schoolOrgId());
        canonical.put("taxonomyVersion", identity.taxonomyVersion());
        canonical.put("termId", identity.termId());
        try {
            byte[] json = WorkflowValues.JSON.writeValueAsBytes(canonical);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        } catch (tools.jackson.core.JacksonException serializationFailure) {
            throw new IllegalStateException("Could not encode release fingerprint", serializationFailure);
        }
    }

    public record ReleaseIdentity(
        String enterpriseOrgId,
        String schoolOrgId,
        String termId,
        String datasetSnapshotId,
        String criteriaHash,
        String profileSnapshotVersion,
        String ruleVersion,
        String taxonomyVersion
    ) {
        public ReleaseIdentity {
            require(enterpriseOrgId, "enterpriseOrgId");
            require(schoolOrgId, "schoolOrgId");
            require(termId, "termId");
            require(datasetSnapshotId, "datasetSnapshotId");
            require(criteriaHash, "criteriaHash");
            require(profileSnapshotVersion, "profileSnapshotVersion");
            require(ruleVersion, "ruleVersion");
            require(taxonomyVersion, "taxonomyVersion");
        }
    }

    private static void require(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }
}
