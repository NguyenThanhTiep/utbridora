package vn.utbridora.auth.files;

import java.nio.channels.Channels;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import vn.utbridora.auth.files.FileContracts.*;
import static vn.utbridora.auth.files.FileScanner.*;

@Component @EnableScheduling
@ConditionalOnProperty(name="app.files.scan.enabled",havingValue="true")
public class FileScanWorker {
    private final FileScanService scans;
    private final FileStorage storage;
    private final FileScanner scanner;
    public FileScanWorker(FileScanService scans,FileStorage storage,FileScanner scanner) {this.scans=scans;this.storage=storage;this.scanner=scanner;}
    @Bean("fileScanScheduler")
    public ThreadPoolTaskScheduler fileScanScheduler() {
        var scheduler=new ThreadPoolTaskScheduler();scheduler.setPoolSize(1);scheduler.setThreadNamePrefix("private-file-scan-");return scheduler;
    }
    @Scheduled(initialDelay=1000,fixedDelay=1000,scheduler="fileScanScheduler")
    public void sweep() {
        try {runOne();}
        catch(RuntimeException deferred) {org.slf4j.LoggerFactory.getLogger(getClass()).warn("File scan deferred; durable lease will recover the attempt");}
    }
    public boolean runOne() {
        var job=scans.claim();if(job==null) return false;
        Result result;
        long started=System.nanoTime();
        try {
            var resource=new ResourceRef(ResourceType.valueOf(job.resourceType()),job.fileId());
            var purpose=FilePurpose.valueOf(job.purpose());var policy=FileUploadPolicy.forResource(resource,purpose);
            if(job.size()<1 || job.size()>policy.maximumBytes() || !policy.contentTypes().contains(job.type())) result=Result.reject(Reason.FILE_INVALID);
            else try(var channel=storage.open(job.storageKey(),job.size(),job.sha256())) {
                FileSafetyValidator.validate(Channels.newInputStream(channel),job.size(),job.type(),job.name(),purpose);
                channel.position(0);
                var remaining=Duration.ofNanos(Duration.ofSeconds(60).toNanos()-(System.nanoTime()-started));
                result=scanner.scan(Channels.newInputStream(channel),job.size(),remaining);
                if(System.nanoTime()-started>=Duration.ofSeconds(60).toNanos()) result=Result.retry(Reason.SCANNER_TIMEOUT);
            }
        } catch(vn.utbridora.auth.operations.OperationFailure invalid) {
            result=invalid.status==422?Result.reject(Reason.FILE_INVALID):Result.retry(Reason.FILE_INTEGRITY_ERROR);
        } catch(java.io.IOException unavailable) {result=Result.retry(Reason.FILE_INTEGRITY_ERROR);}
        catch(IllegalArgumentException | NullPointerException invalid) {result=Result.reject(Reason.FILE_INVALID);}
        return scans.complete(job,result);
    }
}
