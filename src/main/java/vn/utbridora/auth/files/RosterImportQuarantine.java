package vn.utbridora.auth.files;

import java.io.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Scanning happens before the preview transaction, without holding DB/authority locks during I/O. */
@Service
public class RosterImportQuarantine {
    interface Pause {void sleep(long millis) throws InterruptedException;}
    private final FileStorage storage;private final FileScanner scanner;private final Pause pause;
    @Autowired public RosterImportQuarantine(FileStorage storage,FileScanner scanner) {this(storage,scanner,Thread::sleep);}
    RosterImportQuarantine(FileStorage storage,FileScanner scanner,Pause pause) {this.storage=storage;this.scanner=scanner;this.pause=pause;}
    public static final class ScannedRosterFile implements AutoCloseable {
        private final FileStorage.Staged staged;private final TabularFileReader.Table table;private final String name,type;
        private final int attempts;
        private ScannedRosterFile(FileStorage.Staged staged,TabularFileReader.Table table,String name,String type,int attempts) {this.staged=staged;this.table=table;this.name=name;this.type=type;this.attempts=attempts;}
        public TabularFileReader.Table table() {return table;}
        public String sha256() {return staged.sha256();}
        public long size() {return staged.size();}
        public String originalName() {return name;}
        public String contentType() {return type;}
        public int scanAttempts() {return attempts;}
        /** Only this T1 service can create a proof, after a real CLEAN verdict. */
        public String promote(FileStorage storage) {return storage.promote(staged);}
        @Override public void close() {staged.close();}
    }
    public ScannedRosterFile prepare(MultipartFile file) {
        if(file==null || file.isEmpty()) throw failure(422,"FILE_STRUCTURE_INVALID");
        if(file.getSize()>5L*1024*1024) throw failure(413,"FILE_TOO_LARGE");
        String name=FileUploadPolicy.name(file.getOriginalFilename()),type=file.getContentType();
        FileStorage.Staged staged;
        try(var input=file.getInputStream()) {staged=storage.stage(input,5L*1024*1024);}
        catch(IOException unavailable) {throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
        try {
            byte[] bytes;try(var input=staged.open()) {bytes=input.readNBytes(5*1024*1024+1);}
            var table=TabularFileReader.read(bytes,name,type,5000);
            long[] backoff={0,5000,30000,120000};
            for(int attempt=0;attempt<4;attempt++) {
                if(attempt>0) pause.sleep(backoff[attempt]);
                long started=System.nanoTime();FileScanner.Result result;
                try(var input=staged.open()) {result=scanner.scan(input,staged.size(),Duration.ofSeconds(60));}
                if(System.nanoTime()-started>=Duration.ofSeconds(60).toNanos()) result=FileScanner.Result.retry(FileScanner.Reason.SCANNER_TIMEOUT);
                if(result.outcome()==FileScanner.Outcome.CLEAN) return new ScannedRosterFile(staged,table,name,type,attempt+1);
                if(result.outcome()==FileScanner.Outcome.REJECTED) throw failure(422,"FILE_REJECTED");
            }
            throw failure(503,"SCANNER_UNAVAILABLE");
        } catch(InterruptedException interrupted) {Thread.currentThread().interrupt();staged.close();throw failure(503,"SCANNER_UNAVAILABLE");}
        catch(IOException unavailable) {staged.close();throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
        catch(RuntimeException failure) {staged.close();throw failure;}
    }
}
