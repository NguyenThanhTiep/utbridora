package vn.utbridora.auth.modules.cooperation.port;

import java.time.Instant;

public interface TaxAuthorityLookupPort {
    LookupResult lookup(String normalizedTaxCode);
    sealed interface LookupResult permits Found, NotFound, Unavailable {}
    enum TaxpayerStatus { ACTIVE, SUSPENDED, DISSOLVED, UNKNOWN }
    record Found(String taxCode, String officialName, String officialAddress, String taxDepartment,
        TaxpayerStatus status, String statusLabel, String provider, Instant checkedAt) implements LookupResult {}
    record NotFound(String taxCode, String provider, Instant checkedAt) implements LookupResult {}
    record Unavailable(String taxCode, String reasonCode, String provider, Instant checkedAt) implements LookupResult {}
}
