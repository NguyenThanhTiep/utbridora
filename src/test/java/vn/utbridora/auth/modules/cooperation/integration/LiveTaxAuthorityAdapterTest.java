package vn.utbridora.auth.modules.cooperation.integration;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.concurrent.atomic.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort.*;
import static org.junit.jupiter.api.Assertions.*;

class LiveTaxAuthorityAdapterTest {
    HttpServer server;
    final AtomicInteger tokens = new AtomicInteger(), reads = new AtomicInteger();
    final AtomicInteger status = new AtomicInteger(200);
    final AtomicInteger delay = new AtomicInteger();
    final AtomicReference<String> response = new AtomicReference<>("{\"business\":{\"taxCode\":\"0101248141\",\"name\":\"Official business\",\"address\":\"Official address\",\"taxDepartment\":\"Tax office\",\"status\":\"ACTIVE\"}}");
    final MutableClock clock = new MutableClock();
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-27T00:00:00Z");
        public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth/token", exchange -> {
            tokens.incrementAndGet();
            String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(payload.contains("app_id")); assertTrue(payload.contains("app_secret"));
            byte[] bytes = "{\"access_token\":\"test-private-token\",\"expires_in\":90}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.createContext("/", exchange -> {
            reads.incrementAndGet();
            if (delay.get() > 0) try { Thread.sleep(delay.get()); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(status.get(), bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    LiveTaxAuthorityAdapter adapter(String provider) {
        return new LiveTaxAuthorityAdapter(new TaxVerificationProperties("live", false,
            new TaxVerificationProperties.Live(provider, "http://127.0.0.1:" + server.getAddress().getPort(), "id", "secret", 2500), 10, 30), clock);
    }
    @Test void cachesLookupAndTokenUntilTheirSeparateExpiry() {
        var adapter = adapter("casso");
        assertEquals(TaxpayerStatus.ACTIVE, assertInstanceOf(Found.class, adapter.lookup("0101248141")).status());
        assertInstanceOf(Found.class, adapter.lookup("0101248141")); assertEquals(1, reads.get()); assertEquals(1, tokens.get());
        clock.now = clock.now.plusSeconds(11); adapter.lookup("0101248141"); assertEquals(2, reads.get()); assertEquals(1, tokens.get());
        clock.now = clock.now.plusSeconds(100); adapter.lookup("0101248141"); assertEquals(2, tokens.get());
    }
    @Test void mapsOutage429CircuitRecoveryAndWrongTaxpayerWithoutLeakingBodies() {
        var adapter = adapter("casso"); status.set(429); response.set("upstream secret details");
        for (int i = 0; i < 3; i++) assertEquals("UPSTREAM_RATE_LIMITED", assertInstanceOf(Unavailable.class, adapter.lookup("0101248141")).reasonCode());
        assertEquals("CIRCUIT_OPEN", assertInstanceOf(Unavailable.class, adapter.lookup("0101248141")).reasonCode()); assertEquals(3, reads.get());
        clock.now = clock.now.plusSeconds(61); status.set(404);
        assertInstanceOf(NotFound.class, adapter.lookup("0101248141"));
        clock.now = clock.now.plusSeconds(11); status.set(200);
        response.set("{\"business\":{\"taxCode\":\"0300588569\",\"name\":\"Other\",\"address\":\"Other\",\"status\":\"ACTIVE\"}}");
        assertInstanceOf(Unavailable.class, adapter.lookup("0101248141"));
    }
    @Test void legacySuccessDoesNotInventActiveStatus() {
        var adapter = adapter("vietqr-legacy");
        response.set("{\"code\":\"00\",\"data\":{\"id\":\"0101248141\",\"name\":\"Example\",\"address\":\"Example\"}}");
        assertEquals(TaxpayerStatus.UNKNOWN, assertInstanceOf(Found.class, adapter.lookup("0101248141")).status()); assertEquals(0, tokens.get());
        clock.now = clock.now.plusSeconds(11); response.set("{\"code\":\"52\"}");
        assertInstanceOf(NotFound.class, adapter.lookup("0101248141"));
    }
    @Test void missingCredentialsFallBackToClearlyMarkedMock() {
        var properties = new TaxVerificationProperties("live", false, new TaxVerificationProperties.Live("casso", null, "", "", 2500), 86400, 30);
        assertInstanceOf(MockTaxAuthorityAdapter.class, new TaxVerificationConfiguration().taxAuthorityLookupPort(properties));
        assertFalse(properties.toString().contains("test-private-token"));
    }
    @Test void readTimeoutFailsOpenAndExpiredAuthorizationRetriesOnlyOnce() {
        status.set(401);
        assertInstanceOf(Unavailable.class, adapter("casso").lookup("0101248141"));
        assertEquals(2, reads.get()); assertEquals(2, tokens.get());
        status.set(200); delay.set(250);
        var shortTimeout = new LiveTaxAuthorityAdapter(new TaxVerificationProperties("live", false,
            new TaxVerificationProperties.Live("casso", "http://127.0.0.1:" + server.getAddress().getPort(), "id", "secret", 80), 10, 30), clock);
        assertInstanceOf(Unavailable.class, shortTimeout.lookup("0101248141"));
    }
    @Test void mapsVietnameseTaxStatusVariantsAccurately() {
        var adapter = adapter("casso");
        response.set("{\"business\":{\"taxCode\":\"0101248141\",\"name\":\"FPT\",\"address\":\"HN\",\"status\":\"Người nộp thuế đang hoạt động (đã được cấp GCN ĐKT)\"}}");
        assertEquals(TaxpayerStatus.ACTIVE, assertInstanceOf(Found.class, adapter.lookup("0101248141")).status());
        clock.now = clock.now.plusSeconds(11);
        response.set("{\"business\":{\"taxCode\":\"0101248141\",\"name\":\"FPT\",\"address\":\"HN\",\"status\":\"NNT ngừng hoạt động nhưng chưa hoàn thành thủ tục chấm dứt hiệu lực MST\"}}");
        assertEquals(TaxpayerStatus.DISSOLVED, assertInstanceOf(Found.class, adapter.lookup("0101248141")).status());
        clock.now = clock.now.plusSeconds(11);
        response.set("{\"business\":{\"taxCode\":\"0101248141\",\"name\":\"FPT\",\"address\":\"HN\",\"status\":\"NNT đang tạm ngừng kinh doanh có thời hạn\"}}");
        assertEquals(TaxpayerStatus.SUSPENDED, assertInstanceOf(Found.class, adapter.lookup("0101248141")).status());
    }
}
