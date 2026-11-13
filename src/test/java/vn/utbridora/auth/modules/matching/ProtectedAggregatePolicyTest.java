package vn.utbridora.auth.modules.matching;

import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.matching.domain.ProtectedAggregatePolicy;

import static org.junit.jupiter.api.Assertions.*;

class ProtectedAggregatePolicyTest {
    // DEV8 đã sửa từ chưa có oracle độc lập thành kiểm tra ngưỡng band và fingerprint canonical để phục vụ chức năng mã SRS R03.
    @Test void bandsSuppressSmallCountsAndUseOnlySpecifiedRanges() {
        assertEquals("SUPPRESSED", ProtectedAggregatePolicy.eligibleBand(0));
        assertEquals("SUPPRESSED", ProtectedAggregatePolicy.eligibleBand(1));
        assertEquals("SUPPRESSED", ProtectedAggregatePolicy.eligibleBand(4));
        assertEquals("5-9", ProtectedAggregatePolicy.eligibleBand(5));
        assertEquals("5-9", ProtectedAggregatePolicy.eligibleBand(9));
        assertEquals("10-19", ProtectedAggregatePolicy.eligibleBand(10));
        assertEquals("10-19", ProtectedAggregatePolicy.eligibleBand(19));
        assertEquals("20+", ProtectedAggregatePolicy.eligibleBand(20));
        assertThrows(IllegalArgumentException.class, () -> ProtectedAggregatePolicy.eligibleBand(-1));
    }

    @Test void fingerprintIsStableForSameCanonicalIdentityAndChangesWithCriteria() {
        var identity = identity("criteria-a");
        String fingerprint = ProtectedAggregatePolicy.fingerprint(identity);
        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
        assertEquals(fingerprint, ProtectedAggregatePolicy.fingerprint(identity("criteria-a")));
        assertNotEquals(fingerprint, ProtectedAggregatePolicy.fingerprint(identity("criteria-b")));
    }

    private static ProtectedAggregatePolicy.ReleaseIdentity identity(String criteriaHash) {
        return new ProtectedAggregatePolicy.ReleaseIdentity("enterprise", "school", "term", "dataset",
            criteriaHash, "profile-v1", "strict-v1", "taxonomy-v1");
    }
}
