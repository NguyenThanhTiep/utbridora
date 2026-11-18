package vn.utbridora.auth.files;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileScanWorkerTest {
    @Test void blockedScanNeverBlocksSchedulerOrQueuesAnotherAttempt() throws Exception {
        var scans=mock(FileScanService.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var scanThread=new AtomicReference<Thread>();var caller=Thread.currentThread();
        when(scans.claim()).thenAnswer(invocation->{
            scanThread.set(Thread.currentThread());entered.countDown();
            try {release.await(5,TimeUnit.SECONDS);}catch(InterruptedException stopped) {Thread.currentThread().interrupt();}
            return null;
        });
        try(var worker=new FileScanWorker(scans,mock(FileStorage.class),mock(FileScanner.class))) {
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(1),worker::sweep);
                assertTrue(entered.await(3,TimeUnit.SECONDS));assertNotSame(caller,scanThread.get());
                assertEquals("private-file-scan",scanThread.get().getName());
                for(int i=0;i<20;i++) assertTimeoutPreemptively(Duration.ofSeconds(1),worker::sweep);
                verify(scans,times(1)).claim();
            } finally {release.countDown();}
        }
    }
}
