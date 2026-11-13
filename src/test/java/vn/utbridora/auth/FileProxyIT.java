package vn.utbridora.auth;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Real shipped proxy configuration, isolated from all application databases and daily containers. */
class FileProxyIT {
    @Test void uploadHasItsOwnLimitAndProxySizeFailureIsSafeJson() throws Exception {
        try(var nginx=new GenericContainer<>("nginxinc/nginx-unprivileged:1.28-alpine")
            .withExposedPorts(8080).withExtraHost("backend","127.0.0.1")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("web-demo/nginx.conf").toAbsolutePath()),"/etc/nginx/conf.d/default.conf")
            .waitingFor(Wait.forHttp("/healthz").forPort(8080))) {
            nginx.start();assertEquals(0,nginx.execInContainer("nginx","-t").getExitCode());
            String base="http://"+nginx.getHost()+":"+nginx.getMappedPort(8080);
            try(var client=HttpClient.newHttpClient()) {
                var oversized=post(client,base+"/api/v1/files",21*1024*1024+1);
                assertEquals(413,oversized.statusCode());var error=JSON.readTree(oversized.body());
                assertEquals("FILE_TOO_LARGE",error.get("errorCode").asString());
                assertDoesNotThrow(()->UUID.fromString(error.get("requestId").asString()));
                assertEquals(error.get("requestId").asString(),oversized.headers().firstValue("X-Request-ID").orElseThrow());
                assertEquals("nosniff",oversized.headers().firstValue("X-Content-Type-Options").orElseThrow());
                assertTrue(oversized.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
                // Backend intentionally absent: a permitted body reaches proxy_pass (502), not the old 64 KiB limit.
                assertEquals(502,post(client,base+"/api/v1/files",65*1024).statusCode());
                assertEquals(413,post(client,base+"/api/v1/auth/login",65*1024).statusCode());
            }
        }
    }
    private HttpResponse<String> post(HttpClient client,String url,int bytes) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).expectContinue(true)
            .header("Content-Type","multipart/form-data; boundary=test-boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[bytes])).build(),HttpResponse.BodyHandlers.ofString());
    }
}
