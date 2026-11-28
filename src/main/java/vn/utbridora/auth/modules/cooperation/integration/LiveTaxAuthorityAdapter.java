package vn.utbridora.auth.modules.cooperation.integration;

import java.time.*;
import java.util.*;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidator;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort;

/** Bounded best-effort lookup. No credentials, upstream bodies or exception messages leave this adapter. */
public final class LiveTaxAuthorityAdapter implements TaxAuthorityLookupPort {
    private record Cached(LookupResult value, Instant expires) {}
    private final TaxVerificationProperties config;
    private final RestClient client;
    private final Clock clock;
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>();
    private int failures;
    private Instant openUntil = Instant.MIN;
    private String token;
    private Instant tokenExpires = Instant.MIN;
    public LiveTaxAuthorityAdapter(TaxVerificationProperties config) { this(config, Clock.systemUTC()); }
    public LiveTaxAuthorityAdapter(TaxVerificationProperties config, Clock clock) {
        this.config = config; this.clock = clock;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.live().timeoutMs()); factory.setReadTimeout(config.live().timeoutMs());
        client = RestClient.builder().baseUrl(config.live().baseUrl()).requestFactory(factory).build();
    }
    @Override public LookupResult lookup(String raw) {
        String code = TaxCodeValidator.normalize(raw), provider = config.live().provider();
        if (!TaxCodeValidator.validate(code).validEnterprise()) return new NotFound(code, provider, clock.instant());
        synchronized (this) {
            cache.entrySet().removeIf(e -> !e.getValue().expires().isAfter(clock.instant()));
            var cached = cache.get(code); if (cached != null) return cached.value();
            if (clock.instant().isBefore(openUntil)) return unavailable(code, "CIRCUIT_OPEN");
        }
        try {
            JsonNode body;
            if (provider.equals("casso")) {
                try { body = business(code); }
                catch (RestClientResponseException error) {
                    if (error.getStatusCode().value() != 401) throw error;
                    synchronized (this) { token = null; }
                    body = business(code);
                }
            } else body = client.get().uri("/v2/business/{code}", code).retrieve().body(JsonNode.class);
            LookupResult result = parse(code, body);
            synchronized (this) {
                failures = 0; openUntil = Instant.MIN;
                if (cache.size() >= 1000) cache.remove(cache.keySet().iterator().next());
                cache.put(code, new Cached(result, clock.instant().plusSeconds(config.cacheTtlSeconds())));
            }
            return result;
        } catch (RestClientResponseException error) {
            // OAuth errors (including token 404) must never masquerade as an unknown taxpayer.
            return failed(code, error.getStatusCode().value() == 429 ? "UPSTREAM_RATE_LIMITED" : "UPSTREAM_UNAVAILABLE");
        } catch (RuntimeException error) { return failed(code, "UPSTREAM_UNAVAILABLE"); }
    }
    private JsonNode business(String code) {
        String bearer = accessToken();
        try { return client.get().uri("/idkit/v1/business/{code}", code).headers(h -> h.setBearerAuth(bearer)).retrieve().body(JsonNode.class); }
        catch (RestClientResponseException error) {
            if (error.getStatusCode().value() == 404) return null;
            throw error;
        }
    }
    private synchronized String accessToken() {
        if (token != null && clock.instant().isBefore(tokenExpires)) return token;
        JsonNode response = client.post().uri("/oauth/token").contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("app_id", config.live().appId(), "app_secret", config.live().appSecret())).retrieve().body(JsonNode.class);
        String next = text(response, "access_token");
        if (next.isBlank()) throw new IllegalStateException("No access token");
        long expires = response.path("expires_in").asLong(900);
        if (expires <= 0) throw new IllegalStateException("Invalid token lifetime");
        token = next; tokenExpires = clock.instant().plusSeconds(Math.max(1, expires - Math.min(30, expires / 10)));
        return token;
    }
    private LookupResult parse(String requested, JsonNode body) {
        String provider = config.live().provider();
        if (body == null) return new NotFound(requested, provider, clock.instant());
        JsonNode business;
        if (provider.equals("vietqr-legacy")) {
            String code = text(body, "code");
            if (code.equals("51") || code.equals("52")) return new NotFound(requested, provider, clock.instant());
            if (!code.equals("00")) throw new IllegalStateException("Unknown provider response");
            business = body.path("data");
        } else business = body.has("business") ? body.path("business") : body.path("data").path("business");
        String id = text(business, provider.equals("casso") ? "taxCode" : "id");
        String name = text(business, "name"), address = text(business, "address");
        if (!requested.equals(TaxCodeValidator.normalize(id)) || name.isBlank() || address.isBlank()
            || name.length() > 1000 || address.length() > 3000) throw new IllegalStateException("Incomplete or mismatched provider response");
        String status = text(business, "status");
        TaxpayerStatus state = status(status);
        String label = state == TaxpayerStatus.ACTIVE ? "Đang hoạt động" : state == TaxpayerStatus.SUSPENDED ? "Tạm ngừng hoạt động"
            : state == TaxpayerStatus.DISSOLVED ? "Đã giải thể / chấm dứt hoạt động" : "Chưa xác nhận tình trạng hoạt động";
        // Legacy API's 00 means request success, NOT taxpayer ACTIVE.
        return new Found(requested, name, address, text(business, "taxDepartment"), state, label, provider, clock.instant());
    }
    private static TaxpayerStatus status(String raw) {
        String s = vn.utbridora.auth.modules.cooperation.domain.VietnameseBusinessTextNormalizer.normalize(raw);
        if (Set.of("SUSPENDED", "PAUSED", "05").contains(s) || s.contains("TAM NGUNG") || s.contains("TAM KHOA")) return TaxpayerStatus.SUSPENDED;
        if (Set.of("DISSOLVED", "CLOSED", "01").contains(s) || s.contains("GIAI THE") || s.contains("CHAM DUT")
            || s.contains("DONG MA SO THUE") || s.contains("NGUNG HOAT DONG") || s.contains("KHONG HOAT DONG") || s.contains("KHONG CON HOAT DONG")) return TaxpayerStatus.DISSOLVED;
        if (Set.of("ACTIVE", "0", "00").contains(s) || s.contains("DANG HOAT DONG") && !s.contains("KHONG DANG HOAT DONG")) return TaxpayerStatus.ACTIVE;
        return TaxpayerStatus.UNKNOWN;
    }
    private static String text(JsonNode node, String key) {
        if (node == null) return "";
        JsonNode value = node.path(key); return value.isString() || value.isNumber() ? value.asText().strip() : "";
    }
    private synchronized LookupResult failed(String code, String reason) {
        if (++failures >= 3) openUntil = clock.instant().plusSeconds(60);
        return unavailable(code, reason);
    }
    private Unavailable unavailable(String code, String reason) { return new Unavailable(code, reason, config.live().provider(), clock.instant()); }
}
