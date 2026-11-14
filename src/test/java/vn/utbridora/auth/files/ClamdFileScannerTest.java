package vn.utbridora.auth.files;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.files.FileScanner.*;

class ClamdFileScannerTest {
    @Test void onlyExactCleanProtocolResponseCanPass() {
        assertEquals(Outcome.CLEAN,ClamdFileScanner.verdict("stream: OK").outcome());
        assertEquals(Outcome.REJECTED,ClamdFileScanner.verdict("stream: Eicar-Test-Signature FOUND").outcome());
        for(String response:new String[]{"OK","stream: OK\n","other: OK","stream: ERROR","stream: OK ERROR","stream: OK FOUND",""}) {
            assertNotEquals(Outcome.CLEAN,ClamdFileScanner.verdict(response).outcome(),response);
        }
    }
    @Test void sendsLengthPrefixedWholeBytesAndRequiresTerminatedReply() throws Exception {
        byte[] body="synthetic content".getBytes(StandardCharsets.UTF_8);
        try(var server=new ServerSocket(0);var executor=Executors.newSingleThreadExecutor();var scanner=new ClamdFileScanner("127.0.0.1",server.getLocalPort())) {
            var received=executor.submit(()->{
                try(var socket=server.accept()) {
                    var input=new DataInputStream(socket.getInputStream());assertEquals("zINSTREAM\0",new String(input.readNBytes(10),StandardCharsets.US_ASCII));
                    var bytes=new ByteArrayOutputStream();int count;while((count=input.readInt())!=0) bytes.write(input.readNBytes(count));
                    socket.getOutputStream().write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));return bytes.toByteArray();
                }
            });
            assertEquals(Result.clean(),scanner.scan(new ByteArrayInputStream(body),body.length,Duration.ofSeconds(2)));
            assertArrayEquals(body,received.get(3,TimeUnit.SECONDS));
        }
    }
    @Test void timeoutClosesBlockedReadAndNeverReturnsClean() throws Exception {
        try(var server=new ServerSocket(0);var executor=Executors.newSingleThreadExecutor();var scanner=new ClamdFileScanner("127.0.0.1",server.getLocalPort())) {
            var peer=executor.submit(()->{try(var socket=server.accept()) {socket.getInputStream().readAllBytes();}return null;});
            long start=System.nanoTime();
            assertEquals(Reason.SCANNER_TIMEOUT,scanner.scan(new ByteArrayInputStream(new byte[]{1}),1,Duration.ofMillis(100)).reason());
            assertTrue(Duration.ofNanos(System.nanoTime()-start).compareTo(Duration.ofSeconds(2))<0);
            peer.get(2,TimeUnit.SECONDS);
        }
    }
    @Test void missingEndpointExpiredBudgetAndTruncatedInputAreNeverClean() throws Exception {
        try(var scanner=new ClamdFileScanner("",3310)) {
            assertEquals(Reason.SCANNER_UNAVAILABLE,scanner.scan(InputStream.nullInputStream(),1,Duration.ofSeconds(1)).reason());
            assertEquals(Reason.SCANNER_TIMEOUT,scanner.scan(InputStream.nullInputStream(),1,Duration.ZERO).reason());
        }
        try(var server=new ServerSocket(0);var executor=Executors.newSingleThreadExecutor();var scanner=new ClamdFileScanner("127.0.0.1",server.getLocalPort())) {
            var peer=executor.submit(()->{try(var socket=server.accept()) {socket.getInputStream().readAllBytes();}return null;});
            assertEquals(Reason.FILE_INTEGRITY_ERROR,scanner.scan(new ByteArrayInputStream(new byte[]{1}),2,Duration.ofSeconds(1)).reason());
            peer.get(2,TimeUnit.SECONDS);
        }
    }
}
