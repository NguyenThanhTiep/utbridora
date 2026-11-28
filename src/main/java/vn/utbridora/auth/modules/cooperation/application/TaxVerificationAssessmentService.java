package vn.utbridora.auth.modules.cooperation.application;

import java.time.*;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.modules.cooperation.api.TaxAssessmentView;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.integration.TaxVerificationProperties;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;
import static vn.utbridora.auth.modules.cooperation.api.TaxAssessmentView.LookupStatus.*;
import static vn.utbridora.auth.modules.cooperation.api.TaxAssessmentView.Verdict.*;

@Service
@Transactional(propagation = Propagation.NEVER)
public class TaxVerificationAssessmentService {
    private final UserRepository users;
    private final AuthorizationPolicy access;
    private final SchoolDelegationAccess delegations;
    private final VerificationService verifications;
    private final TaxAuthorityLookupPort lookup;
    private final TaxVerificationProperties config;
    private record Bucket(Instant start, int count) {}
    private final Map<String, Bucket> limits = new LinkedHashMap<>();
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired
    public TaxVerificationAssessmentService(UserRepository users, AuthorizationPolicy access, SchoolDelegationAccess delegations,
        VerificationService verifications, TaxAuthorityLookupPort lookup, TaxVerificationProperties config) {
        this(users, access, delegations, verifications, lookup, config, Clock.systemUTC());
    }
    TaxVerificationAssessmentService(UserRepository users, AuthorizationPolicy access, SchoolDelegationAccess delegations,
        VerificationService verifications, TaxAuthorityLookupPort lookup, TaxVerificationProperties config, Clock clock) {
        this.users = users; this.access = access; this.delegations = delegations; this.verifications = verifications;
        this.lookup = lookup; this.config = config; this.clock = clock;
    }
    public TaxAssessmentView lookupForEnterprise(Authentication auth, String organizationId, String raw) {
        String actor = actor(auth), org = uuid(organizationId);
        if (!access.hasOrganization(auth, org, Role.ENTERPRISE, "enterprise_verification:enterprise:manage")) throw failure(403, "ACCESS_DENIED");
        rateLimit(org + ':' + actor);
        var result = assess(raw, null, null);
        actor(auth);
        if (!access.hasOrganization(auth, org, Role.ENTERPRISE, "enterprise_verification:enterprise:manage")) throw failure(403, "ACCESS_DENIED");
        return result;
    }
    public TaxAssessmentView assessForSchool(Authentication auth, String schoolOrgId, String verificationId) {
        String actor = actor(auth), school = uuid(schoolOrgId);
        delegations.require(auth, school, null, "enterprise_verification:school:review");
        // Read transaction ends before remote I/O. This method enforces DRAFT privacy and submitted-snapshot semantics.
        var view = verifications.detail(auth, school, uuid(verificationId), true);
        rateLimit(school + ':' + actor);
        var result = assess(view.data().registrationCode(), view.data().registeredName(), view.data().address());
        // Recheck authorization and version after slow I/O; never return a stale/private school snapshot.
        actor(auth); delegations.require(auth, school, null, "enterprise_verification:school:review");
        var current = verifications.detail(auth, school, uuid(verificationId), true);
        if (current.version() != view.version()) throw failure(409, "VERSION_CONFLICT");
        return result;
    }
    TaxAssessmentView assess(String raw, String name, String address) {
        if (raw == null || raw.length() > 64) throw failure(400, "SCHEMA_VALIDATION");
        var validation = TaxCodeValidator.validate(raw);
        LookupResult result = null;
        if (validation.validEnterprise()) {
            try { result = lookup.lookup(validation.normalizedTaxCode()); }
            catch (RuntimeException outage) { result = new Unavailable(validation.normalizedTaxCode(), "UPSTREAM_UNAVAILABLE", "unknown", clock.instant()); }
        }
        var status = result instanceof Found ? FOUND : result instanceof NotFound ? NOT_FOUND : result instanceof Unavailable ? UNAVAILABLE : SKIPPED;
        Found found = result instanceof Found value ? value : null;
        var nameMatch = found == null || name == null ? null : VietnameseBusinessTextNormalizer.companyName(name, found.officialName());
        var addressMatch = found == null || address == null ? null : VietnameseBusinessTextNormalizer.address(address, found.officialAddress());
        List<String> fields = new ArrayList<>(), reasons = new ArrayList<>();
        reasons.add(validation.message());
        if (nameMatch != null && !nameMatch.matches()) fields.add("registeredName");
        if (addressMatch != null && !addressMatch.matches()) fields.add("address");
        var verdict = UNVERIFIED_OFFLINE;
        if (Boolean.FALSE.equals(validation.checksumValid()) || found != null && (found.status() == TaxpayerStatus.SUSPENDED || found.status() == TaxpayerStatus.DISSOLVED)) {
            verdict = INACTIVE_OR_INVALID; reasons.add("MST không hợp lệ hoặc trạng thái thuế không hoạt động. Cần đối chiếu minh chứng trước khi quyết định.");
        } else if (found != null && found.status() == TaxpayerStatus.ACTIVE) {
            verdict = fields.isEmpty() ? MATCHED_ACTIVE : INFO_MISMATCH;
            reasons.add(fields.isEmpty() ? "Thông tin tra cứu phù hợp; kết quả chỉ hỗ trợ, không thay thế quyết định của nhà trường." : "Tên hoặc địa chỉ khai báo khác dữ liệu tra cứu. Cần kiểm tra lại minh chứng.");
        } else reasons.add("Chưa xác minh trực tuyến được. Vui lòng kiểm tra thủ công giấy phép/PDF đính kèm.");
        String provider = found != null ? found.provider() : result instanceof NotFound n ? n.provider() : result instanceof Unavailable u ? u.provider() : null;
        Instant checked = found != null ? found.checkedAt() : result instanceof NotFound n ? n.checkedAt() : result instanceof Unavailable u ? u.checkedAt() : clock.instant();
        if ("mock".equals(provider)) reasons.add("Đây là dữ liệu minh họa ngoại tuyến, không phải xác nhận từ cơ quan thuế.");
        return new TaxAssessmentView(raw, validation.normalizedTaxCode(), validation.codeKind(), validation.checksumValid(), status,
            found == null ? TaxpayerStatus.UNKNOWN : found.status(), found == null ? null : found.statusLabel(), found == null ? null : found.officialName(),
            found == null ? null : found.officialAddress(), found == null ? null : found.taxDepartment(), provider, checked, verdict,
            nameMatch, addressMatch, List.copyOf(fields), List.copyOf(reasons));
    }
    private String actor(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal principal)
            || users.findById(principal.userId()).filter(u -> u.status == AccountStatus.ACTIVE && u.securityVersion == principal.securityVersion()).isEmpty())
            throw failure(401, "SESSION_EXPIRED");
        return ((AppPrincipal) auth.getPrincipal()).userId();
    }
    private synchronized void rateLimit(String key) {
        Instant now = clock.instant();
        limits.entrySet().removeIf(e -> !now.isBefore(e.getValue().start().plusSeconds(60)));
        Bucket bucket = limits.get(key);
        if (bucket != null && bucket.count() >= config.rateLimitPerMinute())
            throw new OperationFailure(429, "RATE_LIMITED", "Tra cứu quá nhanh. Vui lòng thử lại sau.", (int) Math.max(1, 60 - Duration.between(bucket.start(), now).getSeconds()));
        if (bucket == null && limits.size() >= 10000) throw new OperationFailure(429, "RATE_LIMITED", "Hệ thống tra cứu đang bận. Vui lòng thử lại sau.", 60);
        limits.put(key, bucket == null ? new Bucket(now, 1) : new Bucket(bucket.start(), bucket.count() + 1));
    }
    private static String uuid(String value) {
        if (value == null || !value.matches(vn.utbridora.auth.modules.cooperation.api.CooperationDtos.UUID_PATTERN)) throw failure(400, "SCHEMA_VALIDATION");
        return value.toLowerCase(Locale.ROOT);
    }
    private static OperationFailure failure(int status, String code) { return new OperationFailure(status, code, "Không thể tra cứu trong phạm vi hiện tại.", null); }
}
