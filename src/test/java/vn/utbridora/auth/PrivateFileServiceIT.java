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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.JsonNode;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.files.FileContracts.*;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Dedicated MySQL + private directory. CLEAN below is an explicit fixture, never antivirus evidence. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"app.demo.local.enabled=true","DEMO_PASSWORD=Synthetic-files-test-password-123",
    "app.bootstrap.admin.email=admin@example.test","app.bootstrap.admin.password=Synthetic-files-admin-password-456","app.t5.placement-delivery.enabled=false"})
@ActiveProfiles("local")
class PrivateFileServiceIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("utbridora").withPassword(UUID.randomUUID().toString());
    static final Path ROOT;
    static {try {ROOT=Files.createTempDirectory("utbridora-files-it-");} catch(IOException e) {throw new ExceptionInInitializerError(e);} MYSQL.start();}
    static final byte[] PDF="%PDF-1.7\nSynthetic transport fixture, not a scanned document.\n%%EOF".getBytes(StandardCharsets.UTF_8);
    static String id(int i) {return "00000000-0000-4000-8000-"+String.format("%012d",i);}
    static String key() {return UUID.randomUUID().toString();}
    static final ResourceRef PROFILE=new ResourceRef(ResourceType.STUDENT_PROFILE,id(6));
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("app.files.storage-root",ROOT::toString);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired AuthorizationPolicy core;@Autowired FileService files;@Autowired FileResourceAuthorizer authorizer;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime workspace;
    @Autowired vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions taskActions;
    @AfterEach void context() {SecurityContextHolder.clearContext();org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();}
    @AfterAll static void cleanup() throws IOException {
        // Only direct children of this JVM-created test directory; never a shared storage root.
        try(var paths=Files.list(ROOT)) {for(var path:paths.toList()) Files.delete(path);}Files.delete(ROOT);MYSQL.stop();
    }
    Authentication as(int user) {var auth=core.currentSubject(id(user)).orElseThrow();SecurityContextHolder.getContext().setAuthentication(auth);return auth;}
    MockMultipartFile part(byte[] bytes) {return new MockMultipartFile("file","cv.pdf","application/pdf",bytes);}
    FileDto upload() {return files.upload(as(6),key(),PROFILE,FilePurpose.CV,part(PDF));}
    void syntheticClean(String file) {jdbc.update("UPDATE files SET scan_status='CLEAN' WHERE id=?",file);}

    @Test void realMultipartOwnerMetadataPendingAndForeignScope() throws Exception {
        Session student=login("student"),other=login("intern"),admin=login("admin"),hr=login("hr");
        String command=key();var created=post(student,command,"STUDENT_PROFILE",id(6),"CV","../../cv.pdf","application/pdf",PDF,true,"");assertEquals(202,created.statusCode(),body(created));
        var dto=json(created);String file=dto.get("fileId").asString();
        assertEquals("PENDING",dto.get("scanStatus").asString());assertEquals("cv.pdf",dto.get("originalName").asString());
        assertEquals(PDF.length,dto.get("sizeBytes").asInt());assertFalse(body(created).contains("storageKey"));assertFalse(body(created).contains(ROOT.toString()));
        var metadata=get(student,file,"metadata",null);assertEquals(200,metadata.statusCode());privateHeaders(metadata);
        var pending=get(student,file,"content","garbage");assertError(pending,409,"FILE_NOT_CLEAN");privateHeaders(pending);
        for(var denied:List.of(other,admin,hr)) {
            assertError(get(denied,file,"metadata",null),404,"RESOURCE_NOT_FOUND");
            assertError(get(denied,file,"content","garbage"),404,"RESOURCE_NOT_FOUND");
        }
        var anonymous=new Session(HttpClient.newHttpClient(),"X-CSRF-TOKEN","");assertEquals(401,get(anonymous,file,"metadata",null).statusCode());
        assertEquals(403,post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",PDF,false,"").statusCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings WHERE file_id=?",Integer.class,file));
        assertEquals("PRIVATE",jdbc.queryForObject("SELECT classification FROM files WHERE id=?",String.class,file));
    }
    @Test void cleanFixtureStreamsRangesAndRechecksMembershipOnEveryGet() throws Exception {
        var dto=upload();syntheticClean(dto.fileId());var student=login("student");
        var whole=get(student,dto.fileId(),"content",null);assertEquals(200,whole.statusCode(),body(whole));assertArrayEquals(PDF,whole.body());privateHeaders(whole);
        assertTrue(whole.headers().firstValue("Content-Disposition").orElseThrow().startsWith("attachment;"));
        var slice=get(student,dto.fileId(),"content","bytes=2-8");assertEquals(206,slice.statusCode());assertArrayEquals(Arrays.copyOfRange(PDF,2,9),slice.body());
        assertEquals("bytes 2-8/"+PDF.length,slice.headers().firstValue("Content-Range").orElseThrow());
        assertArrayEquals(Arrays.copyOfRange(PDF,PDF.length-4,PDF.length),get(student,dto.fileId(),"content","bytes=-4").body());
        assertError(get(student,dto.fileId(),"content","bytes=0-1,2-3"),416,"INVALID_RANGE");
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",Integer.class,dto.fileId()));
        var auth=as(6);assertEquals(dto.fileId(),files.requireCleanBound(dto.fileId(),PROFILE,FilePurpose.CV,ActorContext.fromAuthenticatedWorkflow(auth,null)).fileId());
        assertEquals("FILE_BINDING_INVALID",assertThrows(OperationFailure.class,()->files.requireCleanBound(dto.fileId(),PROFILE,FilePurpose.EVIDENCE,ActorContext.fromAuthenticatedWorkflow(auth,null))).code);
        jdbc.update("UPDATE memberships SET active=false WHERE user_id=?",id(6));
        try {
            assertError(get(student,dto.fileId(),"content","bytes=0-1"),404,"RESOURCE_NOT_FOUND");
            assertError(get(student,dto.fileId(),"metadata",null),404,"RESOURCE_NOT_FOUND");
            assertError(get(student,key(),"metadata",null),404,"RESOURCE_NOT_FOUND");
        } finally {jdbc.update("UPDATE memberships SET active=true WHERE user_id=?",id(6));}
        jdbc.update("UPDATE files SET scan_status='REJECTED' WHERE id=?",dto.fileId());assertError(get(student,dto.fileId(),"content",null),409,"FILE_NOT_CLEAN");
        jdbc.update("UPDATE files SET deleted_at=UTC_TIMESTAMP(6) WHERE id=?",dto.fileId());assertError(get(student,dto.fileId(),"metadata",null),404,"RESOURCE_NOT_FOUND");
    }
    @Test void retryIsAtomicAcrossTwentyRequestsAndBindsBinaryAndPurpose() throws Exception {
        var student=as(6);String command=key();long before=objects();var pool=Executors.newFixedThreadPool(8);
        try {
            var calls=new ArrayList<Callable<String>>();for(int i=0;i<20;i++) calls.add(()->{
                SecurityContextHolder.getContext().setAuthentication(student);
                try {return files.upload(student,command,PROFILE,FilePurpose.CV,part(PDF)).fileId();}
                finally {SecurityContextHolder.clearContext();}
            });
            var ids=new HashSet<String>();for(var result:pool.invokeAll(calls)) ids.add(result.get());assertEquals(1,ids.size());
            String file=ids.iterator().next();assertEquals(before+1,objects());
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD' AND idempotency_key=?",Integer.class,command));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED' AND resource_id=?",Integer.class,file));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings WHERE file_id=?",Integer.class,file));
            byte[] changed=PDF.clone();changed[changed.length-1]='x';
            assertEquals(409,assertThrows(OperationFailure.class,()->files.upload(student,command,PROFILE,FilePurpose.CV,part(changed))).status);
            assertEquals(409,assertThrows(OperationFailure.class,()->files.upload(student,command,PROFILE,FilePurpose.EVIDENCE,part(PDF))).status);
            assertEquals(before+1,objects());
        } finally {pool.shutdownNow();}
    }
    @Test void auditFailureRollsBackLedgerMetadataBindingAndPromotedBinary() throws Exception {
        long before=objects();String command=key();var auth=as(6);byte[] unique=Arrays.copyOf(PDF,137);
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT ck_test_file_audit CHECK (NOT(action='FILE_UPLOADED' AND JSON_EXTRACT(metadata_json,'$.sizeBytes')=137))");
        try {
            assertEquals("AUDIT_WRITE_FAILED",assertThrows(OperationFailure.class,()->files.upload(auth,command,PROFILE,FilePurpose.CV,part(unique))).code);
        } finally {jdbc.execute("ALTER TABLE audit_logs DROP CHECK ck_test_file_audit");}
        assertEquals(before,objects());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE size_bytes=137",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",Integer.class,command));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings b LEFT JOIN files f ON f.id=b.file_id WHERE f.id IS NULL",Integer.class));
        assertEquals(202,post(login("student"),command,"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",unique,true,"").statusCode());
    }
    @Test void multipartRejectsBadSchemaMimeAndSizeWithoutPublishingObjects() throws Exception {
        var student=login("student");long before=objects();
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf","not a pdf".getBytes(StandardCharsets.UTF_8),true,""),422,"BUSINESS_VALIDATION");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.exe","application/pdf",PDF,true,""),422,"BUSINESS_VALIDATION");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"SUBMISSION","cv.pdf","application/pdf",PDF,true,""),422,"BUSINESS_VALIDATION");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",PDF,true,field("ownerUserId",id(6))),400,"SCHEMA_VALIDATION");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",PDF,true,field("purpose","CV")),400,"SCHEMA_VALIDATION");
        assertError(post(student,"invalid","STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",PDF,true,""),400,"SCHEMA_VALIDATION");
        assertError(post(student,key(),"WEEKLY_REPORT",key(),"WEEKLY_REPORT","cv.pdf","application/pdf",PDF,true,""),404,"RESOURCE_NOT_FOUND");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",Arrays.copyOf(PDF,10*1024*1024+1),true,""),413,"FILE_TOO_LARGE");
        assertError(post(student,key(),"STUDENT_PROFILE",id(6),"CV","cv.pdf","application/pdf",Arrays.copyOf(PDF,21*1024*1024+1),true,""),413,"FILE_TOO_LARGE");
        assertEquals(before,objects());
    }
    @Test void tamperingAndMissingStorageFailClosedAndRevokedAccountCannotRead() throws Exception {
        var dto=upload();syntheticClean(dto.fileId());var student=login("student");String storageKey=jdbc.queryForObject("SELECT storage_key FROM files WHERE id=?",String.class,dto.fileId());
        Files.write(ROOT.resolve(storageKey),new byte[PDF.length]);assertError(get(student,dto.fileId(),"content",null),503,"FILE_STORAGE_UNAVAILABLE");
        Files.delete(ROOT.resolve(storageKey));assertError(get(student,dto.fileId(),"content",null),503,"FILE_STORAGE_UNAVAILABLE");
        jdbc.update("UPDATE users SET security_version=security_version+1 WHERE id=?",id(6));
        assertEquals(401,get(student,dto.fileId(),"metadata",null).statusCode());
    }
    @Test void ownerReadPortRechecksAccountAndClosedTermCannotReviveUploadOnRetry() {
        var auth=as(6);String command=key();var dto=files.upload(auth,command,PROFILE,FilePurpose.CV,part(PDF));
        jdbc.update("UPDATE users SET locked_until=DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR) WHERE id=?",id(6));
        try {assertEquals(401,assertThrows(OperationFailure.class,()->files.upload(auth,command,PROFILE,FilePurpose.CV,part(PDF))).status);}
        finally {jdbc.update("UPDATE users SET locked_until=NULL WHERE id=?",id(6));}
        jdbc.update("UPDATE terms SET status='CLOSED' WHERE id=?",id(21));
        try {
            assertEquals(dto.fileId(),files.metadata(auth,dto.fileId()).fileId(),"The owner's historical read does not require an ACTIVE term");
            assertEquals(404,assertThrows(OperationFailure.class,()->files.upload(auth,command,PROFILE,FilePurpose.CV,part(PDF))).status);
        } finally {jdbc.update("UPDATE terms SET status='ACTIVE' WHERE id=?",id(21));}
    }
    @Test void verificationUsesCurrentOrganizationAndNeutralNameButNoColleagueShortcut() throws Exception {
        String verification=jdbc.queryForObject("SELECT id FROM enterprise_verifications WHERE enterprise_org_id=?",String.class,id(12));
        jdbc.update("UPDATE enterprise_verifications SET state='DRAFT' WHERE id=?",verification);
        try {
            var hr=login("hr");var response=post(hr,key(),"ENTERPRISE_VERIFICATION",verification,"VERIFICATION","private-company-name.pdf","application/pdf",PDF,true,"");
            assertEquals(202,response.statusCode(),body(response));String file=json(response).get("fileId").asString();
            assertEquals(file+".pdf",json(response).get("originalName").asString());syntheticClean(file);
            assertEquals(200,get(hr,file,"content",null).statusCode());
            assertError(get(login("mentor"),file,"metadata",null),404,"RESOURCE_NOT_FOUND");
            assertError(get(login("school"),file,"metadata",null),404,"RESOURCE_NOT_FOUND");
            jdbc.update("UPDATE enterprise_verifications SET state='SUBMITTED' WHERE id=?",verification);
            assertError(post(hr,key(),"ENTERPRISE_VERIFICATION",verification,"VERIFICATION","cv.pdf","application/pdf",PDF,true,""),409,"STATE_CONFLICT");
            assertEquals(200,get(hr,file,"metadata",null).statusCode());
        } finally {jdbc.update("UPDATE enterprise_verifications SET state='APPROVED' WHERE id=?",verification);}
    }
    @Test void taskUploadUsesRealStartAndCurrentPlacementGateWhileHistoricalReadRemains() throws Exception {
        var mentor=as(5);var request=new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Context-Assignment-Id",workspace.assignments(mentor).stream().filter(a->a.kind().equals("MENTOR")).findFirst().orElseThrow().id());
        // Same header used by existing T3 controllers/authorization.
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(new org.springframework.web.context.request.ServletRequestAttributes(request));
        String group=jdbc.queryForObject("SELECT id FROM internship_groups LIMIT 1",String.class),caseId=jdbc.queryForObject("SELECT id FROM internship_cases LIMIT 1",String.class);
        var task=workspace.createTask(mentor,key(),new vn.utbridora.auth.modules.internship.api.TaskDtos.CreateTask(UUID.fromString(group),"T1-06 transport test","Synthetic Case prerequisite; real Task/start/upload",Instant.now().plusSeconds(3600),vn.utbridora.auth.modules.internship.api.TaskDtos.TaskPriority.NORMAL,List.of(UUID.fromString(caseId)),true));
        String assignment=task.assignments().getFirst().assignmentId().toString();var resource=new ResourceRef(ResourceType.TASK_ASSIGNMENT,assignment);var intern=as(7);
        assertEquals(409,assertThrows(OperationFailure.class,()->files.upload(intern,key(),resource,FilePurpose.SUBMISSION,part(PDF))).status);
        taskActions.start(intern,assignment,key(),new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions.StartAssignment(1L,"IN_PROGRESS"));
        var uploaded=files.upload(intern,key(),resource,FilePurpose.SUBMISSION,part(PDF));syntheticClean(uploaded.fileId());
        assertEquals(503,assertThrows(OperationFailure.class,()->files.upload(intern,key(),resource,FilePurpose.SUBMISSION,new MockMultipartFile("file","work.zip","application/zip",new byte[]{80,75,3,4}))).status);
        jdbc.update("UPDATE placements SET state='CANCELLED',cancelled_at=UTC_TIMESTAMP(6) WHERE id=?",id(99));
        try {
            assertEquals(409,assertThrows(OperationFailure.class,()->files.upload(intern,key(),resource,FilePurpose.SUBMISSION,part(PDF))).status);
            try(var download=files.download(intern,uploaded.fileId(),null)) {assertEquals(PDF.length,download.length());}
        } finally {jdbc.update("UPDATE placements SET state='ACTIVE',cancelled_at=NULL WHERE id=?",id(99));}
        assertFalse(authorizer.authorize(ActorContext.fromAuthenticatedWorkflow(as(6),null),resource,FilePurpose.SUBMISSION,Action.DOWNLOAD,Instant.now()).allowed());
    }
    private record Session(HttpClient client,String header,String token) {}
    private Session login(String account) throws Exception {
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(10)).build();
        var csrf=json(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray()));
        var login=client.send(HttpRequest.newBuilder(uri("/auth/login")).header("Content-Type","application/json").header(csrf.get("headerName").asString(),csrf.get("token").asString())
            .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("email",account+"@example.test","password",account.equals("admin")?"Synthetic-files-admin-password-456":"Synthetic-files-test-password-123")))).build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(200,login.statusCode(),body(login));
        csrf=json(client.send(HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofByteArray()));return new Session(client,csrf.get("headerName").asString(),csrf.get("token").asString());
    }
    private URI uri(String path) {return URI.create("http://localhost:"+port+"/api/v1"+path);}
    private HttpResponse<byte[]> get(Session session,String file,String operation,String range) throws Exception {
        var builder=HttpRequest.newBuilder(uri("/files/"+file+"/"+operation)).timeout(Duration.ofSeconds(30));if(range!=null) builder.header("Range",range);
        return session.client().send(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static final String BOUNDARY="T106-HTTP-test-boundary";
    private static String field(String name,String value) {return "--"+BOUNDARY+"\r\nContent-Disposition: form-data; name=\""+name+"\"\r\n\r\n"+value+"\r\n";}
    private HttpResponse<byte[]> post(Session session,String key,String type,String resource,String purpose,String name,String contentType,byte[] bytes,boolean csrf,String extra) throws Exception {
        var body=new ByteArrayOutputStream();body.write((field("resourceType",type)+field("resourceId",resource)+field("purpose",purpose)+extra+"--"+BOUNDARY+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+contentType+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);body.write(("\r\n--"+BOUNDARY+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(uri("/files")).timeout(Duration.ofSeconds(30)).header("Content-Type","multipart/form-data; boundary="+BOUNDARY).header("Idempotency-Key",key);
        if(csrf) request.header(session.header(),session.token());return session.client().send(request.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    private static String body(HttpResponse<byte[]> response) {return new String(response.body(),StandardCharsets.UTF_8);}
    private static JsonNode json(HttpResponse<byte[]> response) {return JSON.readTree(response.body());}
    private static void assertError(HttpResponse<byte[]> response,int status,String code) {assertEquals(status,response.statusCode(),body(response));assertEquals(code,json(response).get("errorCode").asString(),body(response));}
    private static void privateHeaders(HttpResponse<?> response) {assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));assertEquals("nosniff",response.headers().firstValue("X-Content-Type-Options").orElseThrow());}
    private long objects() throws IOException {try(var paths=Files.list(ROOT)) {return paths.count();}}
}
