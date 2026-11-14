package vn.utbridora.auth.support;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/** Real ClamAV engine with one explicit harmless test signature; not production AV coverage. */
public final class ClamdFixture {
    private ClamdFixture() {}
    public static byte[] eicar() {
        return Base64.getDecoder().decode("WDVPIVAlQEFQWzRcUFpYNTQoUF4pN0NDKTd9JEVJQ0FSLVNUQU5EQVJELUFOVElWSVJVUy1URVNULUZJTEUhJEgrSCo=");
    }
    public static GenericContainer<?> start() {
        String config="""
            Foreground yes
            TCPSocket 3310
            TCPAddr 0.0.0.0
            DatabaseDirectory /tmp/utbridora-signatures
            TemporaryDirectory /tmp
            LogTime yes
            LogVerbose no
            ScanArchive yes
            StreamMaxLength 20M
            MaxScanSize 100M
            MaxFileSize 100M
            MaxFiles 1000
            MaxRecursion 10
            AlertExceedsMax yes
            AlertEncryptedArchive yes
            AlertEncryptedDoc yes
            """;
        String signature="UTBridora.Eicar.Test:0:*:"+HexFormat.of().formatHex(eicar())+"\n";
        var container=new GenericContainer<>("clamav/clamav:1.4.6_base")
            .withExposedPorts(3310)
            .withCopyToContainer(Transferable.of(config.getBytes(StandardCharsets.UTF_8)),"/tmp/utbridora-clamd.conf")
            .withCopyToContainer(Transferable.of(signature.getBytes(StandardCharsets.US_ASCII)),"/tmp/utbridora-signatures/test.ndb")
            .withCreateContainerCmdModifier(command->command.withEntrypoint("clamd"))
            .withCommand("--foreground=true","--config-file=/tmp/utbridora-clamd.conf")
            .waitingFor(Wait.forListeningPort()).withStartupTimeout(Duration.ofSeconds(90));
        container.start();return container;
    }
}
