package vn.utbridora.auth;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.support.ClamdFixture;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;
import static vn.utbridora.auth.files.FileScanner.*;

/** Isolated DB/private directory/real ClamD. Fault tests call trusted verdicts, never SQL CLEAN updates. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-scanner-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-scanner-admin-456","app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
class FileScanningIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora").withPassword(UUID.randomUUID().toString());
    static final GenericContainer<?> CLAMD=ClamdFixture.start();
    static final Path ROOT;
    static {try {ROOT=Files.createTempDirectory("utbridora-scanner-it-");}catch(IOException error) {throw new ExceptionInInitializerError(error);}MYSQL.start();}
    static final Instant NOW=Instant.parse("2026-11-14T01:00:00Z");
    static final byte[] PDF="%PDF-1.7\nSynthetic scanner transport fixture.\n%%EOF".getBytes(StandardCharsets.UTF_8);
    static String id(int value) {return "00000000-0000-4000-8000-"+String.format("%012d",value);}
    @DynamicPropertySource static void config(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);registry.add("spring.datasource.username",MYSQL::getUsername);registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("app.files.storage-root",ROOT::toString);registry.add("app.files.scan.host",CLAMD::getHost);registry.add("app.files.scan.port",()->CLAMD.getMappedPort(3310));
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired FileService files;@Autowired FileScanService scans;@Autowired FileScanner scanner;@Autowired FileStorage storage;@Autowired AuthorizationPolicy core;
    @BeforeEach void isolate() {
        jdbc.update("UPDATE files SET deleted_at=UTC_TIMESTAMP(6) WHERE scan_status='PENDING'");time(NOW);
    }
    @AfterEach void context() {SecurityContextHolder.clearContext();time(Instant.now());}
    @AfterAll static void cleanup() throws IOException {
        try(var paths=Files.list(ROOT)) {for(var path:paths.toList()) Files.delete(path);}Files.delete(ROOT);MYSQL.stop();CLAMD.stop();
    }
    void time(Instant now) {ReflectionTestUtils.setField(scans,"clock",Clock.fixed(now,ZoneOffset.UTC));}
    FileDto upload(byte[] bytes) {
        var actor=core.currentSubject(id(6)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(actor);
        return files.upload(actor,UUID.randomUUID().toString(),new ResourceRef(ResourceType.STUDENT_PROFILE,id(6)),FilePurpose.CV,new MockMultipartFile("file","cv.pdf","application/pdf",bytes));
    }
    FileScanWorker worker(FileScanner scanner) {return new FileScanWorker(scans,storage,scanner);}
    String state(String id) {return jdbc.queryForObject("SELECT scan_status FROM files WHERE id=?",String.class,id);}
    int audits(String id) {return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_SCAN_COMPLETED' AND resource_id=?",Integer.class,id);}
    @Test void realScannerUnlocksOwnerHttpAndRangeButForeignUsersAndRevokedMembershipStayDenied() throws Exception {
        var student=login("student");String file=post(student,PDF);var intern=login("intern");var admin=login("admin");var hr=login("hr");
        assertError(get(student,file,"bytes=0-3"),409,"FILE_NOT_CLEAN");
        for(var other:List.of(intern,admin,hr)) assertError(get(other,file,"garbage"),404,"RESOURCE_NOT_FOUND");
        assertTrue(worker(scanner).runOne());assertEquals("CLEAN",state(file));
        var whole=get(student,file,null);assertEquals(200,whole.statusCode());assertArrayEquals(PDF,whole.body());
        assertEquals("nosniff",whole.headers().firstValue("X-Content-Type-Options").orElseThrow());assertTrue(whole.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        var range=get(student,file,"bytes=2-8");assertEquals(206,range.statusCode());assertArrayEquals(Arrays.copyOfRange(PDF,2,9),range.body());
        for(var other:List.of(intern,admin,hr)) assertError(get(other,file,"bytes=0-1"),404,"RESOURCE_NOT_FOUND");
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=?",id(6));
        try {assertError(get(student,file,"bytes=0-1"),404,"RESOURCE_NOT_FOUND");}
        finally {jdbc.update("UPDATE memberships SET active=true WHERE user_id=?",id(6));}
        assertEquals(1,audits(file));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",Integer.class,file));
        var audit=jdbc.queryForMap("SELECT actor_id,result,metadata_json FROM audit_logs WHERE action='FILE_SCAN_COMPLETED' AND resource_id=?",file);
        assertNull(audit.get("actor_id"));assertEquals("SUCCESS",audit.get("result"));
        assertEquals(Set.of("scanStatus","attemptNo","reasonCode"),new HashSet<>(JSON.readTree(audit.get("metadata_json").toString()).propertyNames()));
    }
    @Test void realClamdDetectsHarmlessEicarFixtureAndNeverPublishesBytes() throws Exception {
        var sample=new ByteArrayOutputStream();sample.write(PDF);sample.write(ClamdFixture.eicar());var dto=upload(sample.toByteArray());
        assertTrue(worker(scanner).runOne());assertEquals("REJECTED",state(dto.fileId()));
        assertEquals("MALWARE_DETECTED",jdbc.queryForObject("SELECT scanner_reason_code FROM files WHERE id=?",String.class,dto.fileId()));
        assertError(get(login("student"),dto.fileId(),null),409,"FILE_NOT_CLEAN");
        assertEquals(1,audits(dto.fileId()));assertEquals("FAILURE",jdbc.queryForObject("SELECT result FROM audit_logs WHERE action='FILE_SCAN_COMPLETED' AND resource_id=?",String.class,dto.fileId()));
    }
    @Test void retryBackoffIsDurableExactAndStopsAfterFourFailedAttempts() {
        var dto=upload(PDF);Instant now=NOW;
        for(int attempt=1;attempt<=4;attempt++) {
            time(now);var job=scans.claim();assertNotNull(job);assertEquals(attempt,job.attempt());
            assertTrue(scans.complete(job,Result.retry(Reason.SCANNER_TIMEOUT)));assertEquals(attempt,audits(dto.fileId()));
            assertEquals(attempt==4?"REJECTED":"PENDING",state(dto.fileId()));
            if(attempt<4) {int delay=new int[]{5,30,120}[attempt-1];time(now.plusSeconds(delay).minusNanos(1000));assertNull(scans.claim());now=now.plusSeconds(delay);}
        }
        time(now.plusSeconds(1000));assertNull(scans.claim());assertEquals(4,audits(dto.fileId()));
        assertNull(jdbc.queryForObject("SELECT scan_next_attempt_at FROM files WHERE id=?",Object.class,dto.fileId()));
    }
    @Test void expiredLeaseIsAuditedAndFencesOldCleanCompletionAfterCrash() {
        var dto=upload(PDF);var old=scans.claim();assertNotNull(old);assertNull(scans.claim());
        time(NOW.plusSeconds(75));assertNull(scans.claim());assertEquals(1,audits(dto.fileId()));assertFalse(scans.complete(old,Result.clean()));
        time(NOW.plusSeconds(80));var fresh=scans.claim();assertNotNull(fresh);assertEquals(2,fresh.attempt());
        assertFalse(scans.complete(old,Result.clean()));assertTrue(scans.complete(fresh,Result.clean()));assertFalse(scans.complete(fresh,Result.clean()));
        assertEquals("CLEAN",state(dto.fileId()));assertEquals(2,audits(dto.fileId()));
    }
    @Test void completionAtLeaseBoundaryCannotPromoteStaleResult() {
        var dto=upload(PDF);var job=scans.claim();time(NOW.plusSeconds(75));assertTrue(scans.complete(job,Result.clean()));
        assertEquals("PENDING",state(dto.fileId()));assertEquals("SCANNER_TIMEOUT",jdbc.queryForObject("SELECT scanner_reason_code FROM files WHERE id=?",String.class,dto.fileId()));
    }
    @Test void concurrentInstancesClaimAndCompleteOnlyOnce() throws Exception {
        var dto=upload(PDF);
        try(var pool=Executors.newFixedThreadPool(8)) {
            var start=new CountDownLatch(1);var calls=new ArrayList<Callable<FileScanService.Job>>();
            for(int i=0;i<12;i++) calls.add(()->{start.await();return scans.claim();});
            var futures=calls.stream().map(pool::submit).toList();start.countDown();var jobs=new ArrayList<FileScanService.Job>();
            for(var future:futures) {var job=future.get(15,TimeUnit.SECONDS);if(job!=null) jobs.add(job);}assertEquals(1,jobs.size());
            var job=jobs.getFirst();var completions=new ArrayList<Callable<Boolean>>();for(int i=0;i<8;i++) completions.add(()->scans.complete(job,Result.clean()));
            int accepted=0;for(var future:pool.invokeAll(completions))if(future.get())accepted++;assertEquals(1,accepted);
        }
        assertEquals(1,audits(dto.fileId()));assertEquals("CLEAN",state(dto.fileId()));
    }
    @Test void auditFailureRollsBackCleanAndCanRetrySameFencedCompletion() {
        var dto=upload(PDF);var job=scans.claim();jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT ck_test_scan_audit CHECK (action<>'FILE_SCAN_COMPLETED' OR resource_id<>'"+dto.fileId()+"')");
        try {assertEquals("AUDIT_WRITE_FAILED",assertThrows(OperationFailure.class,()->scans.complete(job,Result.clean())).code);}
        finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK ck_test_scan_audit");}
        assertEquals("PENDING",state(dto.fileId()));assertEquals(0,audits(dto.fileId()));
        assertTrue(scans.complete(job,Result.clean()));assertEquals("CLEAN",state(dto.fileId()));assertEquals(1,audits(dto.fileId()));
    }
    @Test void corruptionAndMissingScannerFailClosedWithoutCallingAntivirusOrLeakingDiagnostics() throws Exception {
        var dto=upload(PDF);String key=jdbc.queryForObject("SELECT storage_key FROM files WHERE id=?",String.class,dto.fileId());
        Files.write(ROOT.resolve(key),new byte[]{1});
        assertTrue(worker((input,size,time)->{fail("Corrupt bytes must never reach antivirus");return Result.clean();}).runOne());
        assertEquals("PENDING",state(dto.fileId()));assertEquals("FILE_INTEGRITY_ERROR",jdbc.queryForObject("SELECT scanner_reason_code FROM files WHERE id=?",String.class,dto.fileId()));
        assertError(get(login("student"),dto.fileId(),null),409,"FILE_NOT_CLEAN");
        time(NOW.plusSeconds(5));var job=scans.claim();assertNotNull(job);assertTrue(scans.complete(job,Result.retry(Reason.SCANNER_UNAVAILABLE)));
        assertEquals("PENDING",state(dto.fileId()));
    }
    private record Session(HttpClient client,String header,String token) {}
    private URI uri(String path) {return URI.create("http://localhost:"+port+"/api/v1"+path);}
    private Session login(String account) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
        var csrf=JSON.readTree(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray()).body());
        var login=client.send(HttpRequest.newBuilder(uri("/auth/login")).header("Content-Type","application/json").header(csrf.get("headerName").asString(),csrf.get("token").asString())
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",account+"@example.test","password",account.equals("admin")?"Synthetic-scanner-admin-456":"Synthetic-scanner-password-123")))).build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,login.statusCode());
        csrf=JSON.readTree(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray()).body());return new Session(client,csrf.get("headerName").asString(),csrf.get("token").asString());
    }
    private String post(Session session,byte[] bytes) throws Exception {
        String boundary="T107-HTTP-test-boundary";var body=new ByteArrayOutputStream();
        for(var entry:Map.of("resourceType","STUDENT_PROFILE","resourceId",id(6),"purpose","CV").entrySet()) body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"cv.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var response=session.client().send(HttpRequest.newBuilder(uri("/files")).header("Content-Type","multipart/form-data; boundary="+boundary).header(session.header(),session.token()).header("Idempotency-Key",UUID.randomUUID().toString()).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(202,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));return JSON.readTree(response.body()).get("fileId").asString();
    }
    private HttpResponse<byte[]> get(Session session,String file,String range) throws Exception {
        var request=HttpRequest.newBuilder(uri("/files/"+file+"/content")).timeout(Duration.ofSeconds(30));if(range!=null)request.header("Range",range);
        return session.client().send(request.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static void assertError(HttpResponse<byte[]> response,int status,String code) {assertEquals(status,response.statusCode(),new String(response.body(),StandardCharsets.UTF_8));assertEquals(code,JSON.readTree(response.body()).get("errorCode").asString());}
}
