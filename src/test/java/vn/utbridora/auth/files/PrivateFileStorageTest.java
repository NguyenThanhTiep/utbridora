package vn.utbridora.auth.files;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.files.FileContracts.*;

class PrivateFileStorageTest {
    @TempDir Path root;
    final byte[] pdf="%PDF-1.7\nsynthetic test document\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @Test void streamsHashesPromotesAndVerifiesBytes() throws Exception {
        var storage=new PrivateFileStorage(root.toString());String key;
        try(var staged=storage.stage(new ByteArrayInputStream(pdf),pdf.length)) {
            assertEquals(pdf.length,staged.size());
            assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)),staged.sha256());
            key=storage.promote(staged);
            try(var channel=storage.open(key,pdf.length,staged.sha256())) {
                var out=java.nio.ByteBuffer.allocate(pdf.length);channel.read(out);assertArrayEquals(pdf,out.array());
            }
            Files.write(root.resolve(key),new byte[pdf.length]);
            assertEquals(503,assertThrows(OperationFailure.class,()->storage.open(key,pdf.length,staged.sha256())).status);
        }
        storage.delete(key);assertEquals(0,objects());
    }
    @Test void rejectedOrAbandonedStreamsLeaveNoParts() throws Exception {
        var storage=new PrivateFileStorage(root.toString());
        assertEquals(413,assertThrows(OperationFailure.class,()->storage.stage(new ByteArrayInputStream(pdf),pdf.length-1)).status);
        assertEquals(422,assertThrows(OperationFailure.class,()->storage.stage(new ByteArrayInputStream(new byte[0]),100)).status);
        assertEquals(503,assertThrows(OperationFailure.class,()->storage.stage(new InputStream(){public int read() throws IOException {throw new IOException("test");}},100)).status);
        try(var staged=storage.stage(new ByteArrayInputStream(pdf),100)) {assertEquals(1,objects());}
        assertEquals(0,objects());
    }
    @ParameterizedTest @ValueSource(strings={"../escape","/tmp/file","C:\\private\\file","file.pdf","00000000-0000-4000-8000-000000000000.blob/../escape"})
    void clientCannotChooseStoragePath(String key) {
        assertEquals(503,assertThrows(OperationFailure.class,()->new PrivateFileStorage(root.toString()).open(key,1,"a")).status);
    }
    @ParameterizedTest @ValueSource(strings={"","relative/path"})
    void missingOrRelativeRootFailsClosed(String path) {
        assertEquals(503,assertThrows(OperationFailure.class,()->new PrivateFileStorage(path).stage(new ByteArrayInputStream(pdf),100)).status);
    }
    @Test void rejectsSymlinkObjectAndRoot() throws Exception {
        Path actual=Files.write(root.resolve("actual.pdf"),pdf),link=root.resolve(UUID.randomUUID()+".blob");
        try {Files.createSymbolicLink(link,actual);} catch(FileSystemException | UnsupportedOperationException unavailable) {
            Assumptions.abort("OS does not permit creating symlinks for this test");
        }
        assertEquals(503,assertThrows(OperationFailure.class,()->new PrivateFileStorage(root.toString()).open(link.getFileName().toString(),pdf.length,"a")).status);
        Path directory=Files.createDirectory(root.resolve("private")),directoryLink=root.resolve("linked");Files.createSymbolicLink(directoryLink,directory);
        assertEquals(503,assertThrows(OperationFailure.class,()->new PrivateFileStorage(directoryLink.toString()).stage(new ByteArrayInputStream(pdf),100)).status);
    }
    @Test void signatureExtensionAndPurposeAreCheckedWithoutClaimingAV() {
        var storage=new PrivateFileStorage(root.toString());
        var policy=FileUploadPolicy.forResource(new ResourceRef(ResourceType.STUDENT_PROFILE,UUID.randomUUID().toString()),FilePurpose.CV);
        try(var staged=storage.stage(new ByteArrayInputStream(pdf),100)) {
            assertDoesNotThrow(()->FileUploadPolicy.content(staged,policy,"application/pdf","cv.pdf"));
            assertEquals(422,assertThrows(OperationFailure.class,()->FileUploadPolicy.content(staged,policy,"image/png","cv.png")).status);
            assertEquals(422,assertThrows(OperationFailure.class,()->FileUploadPolicy.content(staged,policy,"application/pdf","cv.exe")).status);
        }
        var task=FileUploadPolicy.forResource(new ResourceRef(ResourceType.TASK_ASSIGNMENT,UUID.randomUUID().toString()),FilePurpose.SUBMISSION);
        try(var staged=storage.stage(new ByteArrayInputStream(new byte[]{80,75,3,4}),100)) {
            assertEquals(503,assertThrows(OperationFailure.class,()->FileUploadPolicy.content(staged,task,"application/zip","work.zip")).status);
        }
        assertEquals(422,assertThrows(OperationFailure.class,()->FileUploadPolicy.forResource(new ResourceRef(ResourceType.STUDENT_PROFILE,UUID.randomUUID().toString()),FilePurpose.SUBMISSION)).status);
    }
    @Test void filenamesAreDisplayOnlyAndCannotInjectHeaders() {
        assertEquals("cv.pdf",FileUploadPolicy.name("C:\\fakepath\\cv.pdf"));assertEquals("cv.pdf",FileUploadPolicy.name("../../cv.pdf"));
        assertEquals("é.pdf",FileUploadPolicy.name("e\u0301.pdf"));
        for(String name:List.of("\r\nInjected: x.pdf","../",".","..","a".repeat(256)))
            assertEquals(400,assertThrows(OperationFailure.class,()->FileUploadPolicy.name(name)).status);
    }
    private long objects() throws IOException {try(var paths=Files.list(root)) {return paths.count();}}
}
