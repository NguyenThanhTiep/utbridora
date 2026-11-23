package vn.utbridora.auth.modules.matching.semantic;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticFixtures.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.*;

/** Explicit -Dtest=...Probe only. Calls the test-only Python harness, not an E5 model. */
class PythonSemanticProtocolProbe {
    @Test void javaClientValidatesActualFastApiResponseAndMetadata() {
        String base=System.getProperty("semantic.protocol.url","");
        assertTrue(base.startsWith("http://127.0.0.1:"),"Explicit loopback protocol harness is required");
        try(var client=new SemanticHttpClient(URI.create(base),TOKEN,REVISION,Duration.ofSeconds(3))) {
            var result=client.compare("Required JAVA",List.of(new Passage("0","JAVA SQL"),new Passage("1"," "),
                new Passage("2","X".repeat(6000))),Duration.ofSeconds(3));
            assertEquals(3,result.size());assertEquals("READY",result.get(0).status());assertNotNull(result.get(0).score());
            assertEquals("NO_DATA",result.get(1).status());assertNull(result.get(1).score());
            assertTrue(result.get(2).limited());assertEquals(4,result.get(2).passageChunks());
        }
    }
}
