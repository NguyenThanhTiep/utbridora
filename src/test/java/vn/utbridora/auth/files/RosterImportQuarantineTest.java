package vn.utbridora.auth.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.utbridora.auth.operations.OperationFailure;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class RosterImportQuarantineTest {
    final byte[] bytes="studentCode,email,fullName,majorCode,termCode\n0001,a@x.test,First Name,IT,T1".getBytes(StandardCharsets.UTF_8);
    FileStorage.Staged stage() throws Exception {
        var staged=mock(FileStorage.Staged.class);when(staged.size()).thenReturn((long)bytes.length);when(staged.open()).thenAnswer(i->new ByteArrayInputStream(bytes));return staged;
    }
    @Test void unavailableScanRetriesWithBoundedPolicyAndNeverProducesACleanProof() throws Exception {
        var storage=mock(FileStorage.class);var scanner=mock(FileScanner.class);var stage=stage();when(storage.stage(any(),anyLong())).thenReturn(stage);
        when(scanner.scan(any(),anyLong(),any())).thenReturn(FileScanner.Result.retry(FileScanner.Reason.SCANNER_UNAVAILABLE));
        var pauses=new ArrayList<Long>();var service=new RosterImportQuarantine(storage,scanner,pauses::add);
        var failure=assertThrows(OperationFailure.class,()->service.prepare(new MockMultipartFile("file","students.csv","text/csv",bytes)));
        assertEquals(503,failure.status);assertEquals("SCANNER_UNAVAILABLE",failure.code);assertEquals(List.of(5000L,30000L,120000L),pauses);
        verify(scanner,times(4)).scan(any(),eq((long)bytes.length),eq(Duration.ofSeconds(60)));verify(stage).close();verify(storage,never()).promote(any());
    }
    @Test void malwareHasNoRetryAndCleanProofRequiresARealPortVerdict() throws Exception {
        var storage=mock(FileStorage.class);var scanner=mock(FileScanner.class);var stage=stage();when(storage.stage(any(),anyLong())).thenReturn(stage);
        when(scanner.scan(any(),anyLong(),any())).thenReturn(FileScanner.Result.reject(FileScanner.Reason.MALWARE_DETECTED));
        var service=new RosterImportQuarantine(storage,scanner,millis->{throw new AssertionError("Malware must not retry");});
        assertEquals("FILE_REJECTED",assertThrows(OperationFailure.class,()->service.prepare(new MockMultipartFile("file","x.csv","text/csv",bytes))).code);
        verify(stage).close();when(scanner.scan(any(),anyLong(),any())).thenReturn(FileScanner.Result.clean());
        try(var clean=service.prepare(new MockMultipartFile("file","x.csv","text/csv",bytes))) {assertEquals(1,clean.scanAttempts());assertEquals(1,clean.table().rows().size());}
    }
}
