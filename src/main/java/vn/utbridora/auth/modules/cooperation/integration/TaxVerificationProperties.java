package vn.utbridora.auth.modules.cooperation.integration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.cooperation.tax-verification")
public record TaxVerificationProperties(String mode, boolean strictSubmitValidation, Live live, long cacheTtlSeconds, int rateLimitPerMinute) {
    public TaxVerificationProperties {
        mode = mode == null ? "mock" : mode;
        if (!mode.equals("mock") && !mode.equals("live")) throw new IllegalArgumentException("Unsupported tax verification mode");
        live = live == null ? new Live(null, null, null, null, 2500) : live;
        cacheTtlSeconds = cacheTtlSeconds <= 0 ? 86400 : Math.min(cacheTtlSeconds, 86400);
        rateLimitPerMinute = rateLimitPerMinute <= 0 ? 30 : rateLimitPerMinute;
    }
    public record Live(String provider, String baseUrl, String appId, String appSecret, int timeoutMs) {
        public Live {
            provider = provider == null ? "casso" : provider;
            if (!provider.equals("casso") && !provider.equals("vietqr-legacy")) throw new IllegalArgumentException("Unsupported tax provider");
            baseUrl = baseUrl == null ? "https://openapi.casso.vn" : baseUrl.strip();
            appId = appId == null ? "" : appId; appSecret = appSecret == null ? "" : appSecret;
            timeoutMs = timeoutMs <= 0 ? 2500 : Math.min(timeoutMs, 10000);
        }
        public boolean configured() { return !baseUrl.isBlank() && (provider.equals("vietqr-legacy") || !appId.isBlank() && !appSecret.isBlank()); }
        @Override public String toString() { return "Live[provider=" + provider + "]"; }
    }
}
