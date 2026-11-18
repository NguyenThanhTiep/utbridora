package vn.utbridora.auth.files;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import static vn.utbridora.auth.files.FileScanner.Reason.*;

/** ClamD zINSTREAM: no filesystem paths, client-controlled hosts or shell commands. */
@Component
public final class ClamdFileScanner implements FileScanner,AutoCloseable {
    private final String host;
    private final int port;
    private final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(r->{
        var thread=new Thread(r,"clamd-deadline");thread.setDaemon(true);return thread;
    });
    public ClamdFileScanner(@Value("${app.files.scan.host:}") String host,@Value("${app.files.scan.port:3310}") int port) {
        this.host=host;this.port=port;
        if(port<1 || port>65535) throw new IllegalArgumentException("Invalid scanner port");
    }
    @Override public Result scan(InputStream input,long size,Duration remaining) {
        if(remaining.isNegative() || remaining.isZero()) return Result.retry(SCANNER_TIMEOUT);
        if(host==null || host.isBlank()) return Result.retry(SCANNER_UNAVAILABLE);
        long nanos=Math.min(remaining.toNanos(),Duration.ofSeconds(60).toNanos());
        long end=System.nanoTime()+nanos;
        try(var socket=new Socket()) {
            // Closing the socket bounds writes as well as reads; SO_TIMEOUT alone does not bound writes.
            var deadline=deadlines.schedule(()->{try {socket.close();}catch(IOException ignored) {}},nanos,TimeUnit.NANOSECONDS);
            try {
                int timeout=(int)Math.max(1,Math.min(60000,TimeUnit.NANOSECONDS.toMillis(nanos)));
                socket.connect(new InetSocketAddress(host,port),Math.min(timeout,5000));socket.setSoTimeout(timeout);
                var output=new DataOutputStream(socket.getOutputStream());output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
                byte[] buffer=new byte[8192];long sent=0;int read;
                while((read=input.read(buffer))!=-1) {
                    sent+=read;if(sent>size) return Result.reject(FILE_INTEGRITY_ERROR);
                    output.writeInt(read);output.write(buffer,0,read);
                }
                if(sent!=size) return Result.reject(FILE_INTEGRITY_ERROR);
                output.writeInt(0);output.flush();
                var response=new ByteArrayOutputStream();var reply=socket.getInputStream();
                for(int i=0;i<4096;i++) {
                    int b=reply.read();if(b<0) return Result.retry(SCANNER_PROTOCOL_ERROR);
                    if(b==0) {
                        if(System.nanoTime()>=end) return Result.retry(SCANNER_TIMEOUT);
                        return verdict(response.toString(StandardCharsets.US_ASCII));
                    }
                    if(b<32 || b>126) return Result.retry(SCANNER_PROTOCOL_ERROR);
                    response.write(b);
                }
                return Result.retry(SCANNER_PROTOCOL_ERROR);
            } finally {deadline.cancel(false);}
        } catch(SocketTimeoutException timeout) {return Result.retry(SCANNER_TIMEOUT);}
        catch(IOException unavailable) {return Result.retry(System.nanoTime()>=end?SCANNER_TIMEOUT:SCANNER_UNAVAILABLE);}
    }
    static Result verdict(String response) {
        if(response.equals("stream: OK")) return Result.clean();
        if(response.matches("stream: [A-Za-z0-9_.:() /+\\-]{1,512} FOUND")) return Result.reject(MALWARE_DETECTED);
        return Result.retry(SCANNER_PROTOCOL_ERROR);
    }
    @Override @jakarta.annotation.PreDestroy public void close() {deadlines.shutdownNow();}
}
