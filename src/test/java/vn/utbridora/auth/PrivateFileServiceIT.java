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

    @Test void qa01T106RetryAfterMembershipRevocationCannotReplaySuccess() throws Exception {
        // QA01: a historical successful idempotency record must not bypass current authorization.
        var student = as(6);
        String requestKey = key();
        var created = files.upload(student, requestKey, PROFILE, FilePurpose.CV, part(PDF));
        long objectCount = objects();
        int operationCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
            Integer.class, requestKey);
        int auditCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED' AND resource_id=?",
            Integer.class, created.fileId());

        jdbc.update("UPDATE memberships SET active=false WHERE user_id=?", id(6));
        try {
            var denied = assertThrows(OperationFailure.class,
                () -> files.upload(student, requestKey, PROFILE, FilePurpose.CV, part(PDF)));
            assertEquals(403, denied.status, "Revoked membership must block upload retry");
            assertEquals("ACCESS_DENIED", denied.code);
            assertEquals(objectCount, objects(), "Denied retry must not write a new private object");
            assertEquals(operationCount, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                Integer.class, requestKey));
            assertEquals(auditCount, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED' AND resource_id=?",
                Integer.class, created.fileId()));
        } finally {
            jdbc.update("UPDATE memberships SET active=true WHERE user_id=?", id(6));
        }

        // After restoring the original right, the same request remains idempotent.
        var replay = files.upload(student, requestKey, PROFILE, FilePurpose.CV, part(PDF));
        assertEquals(created.fileId(), replay.fileId());
        assertEquals(objectCount, objects());
    }

    @Test void qa01T106ForgedActorRoleAndForeignProfileUploadAreDenied() throws Exception {
        // QA01 Security 03: caller identity comes from the authenticated session, never multipart fields.
        var foreignStudent = login("intern");
        var ownerStudent = login("student");
        long objectsBefore = objects();
        int filesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int bindingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);
        String foreignKey = key(), reverseKey = key(), actorKey = key(), roleKey = key();

        // A valid account cannot upload a private CV into another student's profile.
        assertError(post(foreignStudent, foreignKey, "STUDENT_PROFILE", id(6), "CV",
                "cv.pdf", "application/pdf", PDF, true, ""), 404, "RESOURCE_NOT_FOUND");
        assertError(post(ownerStudent, reverseKey, "STUDENT_PROFILE", id(7), "CV",
                "cv.pdf", "application/pdf", PDF, true, ""), 404, "RESOURCE_NOT_FOUND");

        // Multipart actor/role spoofing must be rejected by the strict API schema.
        assertError(post(foreignStudent, actorKey, "STUDENT_PROFILE", id(6), "CV",
                "cv.pdf", "application/pdf", PDF, true, field("actorId", id(6))), 400, "SCHEMA_VALIDATION");
        assertError(post(foreignStudent, roleKey, "STUDENT_PROFILE", id(6), "CV",
                "cv.pdf", "application/pdf", PDF, true, field("role", "ADMIN")), 400, "SCHEMA_VALIDATION");

        // All denied requests must leave file metadata, bindings, audit and physical objects unchanged.
        assertEquals(objectsBefore, objects());
        assertEquals(filesBefore, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(bindingsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class));
        assertEquals(auditBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class));
        for (String deniedKey : List.of(foreignKey, reverseKey, actorKey, roleKey)) {
            assertEquals(0, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?", Integer.class, deniedKey));
        }
    }

    @Test void qa01T106DeniedForeignReadHasNoSuccessAudit() throws Exception {
        // QA01 Security 04: a denied metadata/download request must not create a success audit.
        var owner = as(6);
        var uploaded = files.upload(owner, key(), PROFILE, FilePurpose.CV, part(PDF));
        syntheticClean(uploaded.fileId()); // Test-only fixture; this is NOT an antivirus scan.
        String fileId = uploaded.fileId();
        int before = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
            Integer.class, fileId);

        for (var unauthorized : List.of(login("intern"), login("admin"), login("hr"))) {
            var metadata = get(unauthorized, fileId, "metadata", null);
            assertError(metadata, 404, "RESOURCE_NOT_FOUND");
            privateHeaders(metadata);

            // The caller must not learn that the file exists, even with a syntactically valid Range.
            var content = get(unauthorized, fileId, "content", "bytes=0-1");
            assertError(content, 404, "RESOURCE_NOT_FOUND");
            privateHeaders(content);
            assertFalse(body(content).contains("Synthetic transport fixture"),
                "Denied download must not return private file bytes");
            assertFalse(body(metadata).contains(ROOT.toString()),
                "Denied metadata must not expose the private storage path");
        }

        assertEquals(before, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
            Integer.class, fileId), "Denied reads must not write FILE_DOWNLOADED success audit");

        // Positive control: owner can still read and must generate exactly one success audit.
        var allowed = get(login("student"), fileId, "content", "bytes=0-1");
        assertEquals(206, allowed.statusCode(), body(allowed));
        assertArrayEquals(Arrays.copyOfRange(PDF, 0, 2), allowed.body());
        assertEquals(before + 1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
            Integer.class, fileId));
    }

    @Test void qa01T106AnonymousUploadWithValidCsrfCannotCreatePrivateFile() throws Exception {
        // TC02: an anonymous HTTP session may hold a valid CSRF token but must not upload files.
        // This isolates authentication (401) from the separate no-CSRF rejection (403).
        String command = key();
        long privateObjectsBefore = objects();
        int filesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int bindingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int successfulAuditBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);

        var browser = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        var csrfResponse = browser.send(
            HttpRequest.newBuilder(uri("/auth/csrf")).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, csrfResponse.statusCode(), body(csrfResponse));
        var csrf = json(csrfResponse);
        var anonymous = new Session(browser,
            csrf.get("headerName").asString(), csrf.get("token").asString());

        var denied = post(anonymous, command, "STUDENT_PROFILE", id(6), "CV",
            "cv.pdf", "application/pdf", PDF, true, "");
        assertEquals(401, denied.statusCode(), body(denied));
        assertFalse(body(denied).contains(ROOT.toString()),
            "Anonymous rejection must not expose a private filesystem path");
        assertFalse(body(denied).contains("Synthetic transport fixture"),
            "Anonymous rejection must not echo uploaded bytes");

        assertEquals(filesBefore, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class),
            "Unauthenticated POST must not create file metadata");
        assertEquals(bindingsBefore, jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class),
            "Unauthenticated POST must not create a file binding");
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD' AND idempotency_key=?",
            Integer.class, command), "Unauthenticated POST must not create a success ledger entry");
        assertEquals(successfulAuditBefore, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class),
            "Unauthenticated POST must not create a FILE_UPLOADED audit");
        assertEquals(privateObjectsBefore, objects(),
            "Unauthenticated POST must not promote a private binary object");
    }

    @Test void qa01T106ActiveOwnerWithoutFileUploadPermissionIsDenied() throws Exception {
        // TC04: an active student can own the profile but still needs file:upload.
        var student = login("student");
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM memberships WHERE user_id=? AND role='STUDENT' AND active=true",
            Integer.class, id(6)), "Student membership must remain active");
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions WHERE role='STUDENT' AND permission_code='file:upload'",
            Integer.class), "Fixture must initially grant file:upload");
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions WHERE role='STUDENT' AND permission_code='file:download'",
            Integer.class), "Fixture must retain file:download");

        String requestKey = key();
        long objectsBefore = objects();
        int filesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int bindingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int auditBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);

        // Modify only the isolated Testcontainers database; restore mapping in finally.
        int removed = jdbc.update(
            "DELETE FROM role_permissions WHERE role='STUDENT' AND permission_code='file:upload'");
        try {
            assertEquals(1, removed, "Must remove exactly the test fixture permission");
            assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM memberships m JOIN role_permissions p ON p.role=m.role " +
                "WHERE m.user_id=? AND m.active=true AND p.permission_code='file:upload'",
                Integer.class, id(6)), "No other active membership may grant upload");
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM memberships WHERE user_id=? AND role='STUDENT' AND active=true",
                Integer.class, id(6)), "Membership itself must remain active");

            var denied = post(student, requestKey, "STUDENT_PROFILE", id(6), "CV",
                "cv.pdf", "application/pdf", PDF, true, "");
            assertError(denied, 403, "ACCESS_DENIED");
            assertFalse(body(denied).contains(ROOT.toString()), "No storage path leak");
            assertEquals(objectsBefore, objects(), "Denied upload must not store bytes");
            assertEquals(filesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM files", Integer.class), "No file metadata");
            assertEquals(bindingsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_bindings", Integer.class), "No file binding");
            assertEquals(auditBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class),
                "No successful upload audit");
            assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD' AND idempotency_key=?",
                Integer.class, requestKey), "Denied request must not create upload ledger");
        } finally {
            if (removed == 1) {
                jdbc.update("INSERT INTO role_permissions (role,permission_code) VALUES ('STUDENT','file:upload')");
            }
        }

        // Positive control: restored permission allows the same owner to upload.
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM role_permissions WHERE role='STUDENT' AND permission_code='file:upload'",
            Integer.class));
        var allowed = post(student, key(), "STUDENT_PROFILE", id(6), "CV",
            "cv.pdf", "application/pdf", PDF, true, "");
        assertEquals(202, allowed.statusCode(), body(allowed));
    }

    @Test void qa01T106MissingRequiredMultipartFieldsDoNotCreatePrivateFile() throws Exception {
        // QA01 T1-06-TC05: every required multipart field must be present exactly once.
        // Existing DEV coverage handles unknown/duplicated fields; this fills the missing-field matrix.
        var student = login("student");
        long objectsBefore = objects();
        int filesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int bindingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int auditsBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);

        for (String omitted : List.of("resourceType", "resourceId", "purpose", "file")) {
            String requestKey = key();
            var payload = new ByteArrayOutputStream();
            if (!"resourceType".equals(omitted)) {
                payload.write(field("resourceType", "STUDENT_PROFILE").getBytes(StandardCharsets.UTF_8));
            }
            if (!"resourceId".equals(omitted)) {
                payload.write(field("resourceId", id(6)).getBytes(StandardCharsets.UTF_8));
            }
            if (!"purpose".equals(omitted)) {
                payload.write(field("purpose", "CV").getBytes(StandardCharsets.UTF_8));
            }
            if (!"file".equals(omitted)) {
                payload.write(("--" + BOUNDARY + "\r\n" +
                    "Content-Disposition: form-data; name=\"file\"; filename=\"cv.pdf\"\r\n" +
                    "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                payload.write(PDF);
                payload.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            payload.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));

            var request = HttpRequest.newBuilder(uri("/files"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header("Idempotency-Key", requestKey)
                .header(student.header(), student.token())
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray()))
                .build();
            var denied = student.client().send(request, HttpResponse.BodyHandlers.ofByteArray());
            assertError(denied, 400, "SCHEMA_VALIDATION");
            assertEquals(objectsBefore, objects(), "No bytes after omitting " + omitted);
            assertEquals(filesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM files", Integer.class), "No file row after omitting " + omitted);
            assertEquals(bindingsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_bindings", Integer.class), "No binding after omitting " + omitted);
            assertEquals(auditsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class),
                "No success audit after omitting " + omitted);
            assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                Integer.class, requestKey), "No operation record after omitting " + omitted);
        }
    }

    @Test void qa01T106DuplicateMultipartFieldsDoNotCreatePrivateFile() throws Exception {
        // TC05 completion: multipart scalar fields and file must appear exactly once.
        // The previous QA01 method covered missing fields; this covers duplicate fields.
        var student = login("student");
        long objectsBefore = objects();
        int filesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int bindingsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int auditsBefore = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);

        String duplicateFile = "--" + BOUNDARY + "\r\n" +
            "Content-Disposition: form-data; name=\"file\"; filename=\"second.pdf\"\r\n" +
            "Content-Type: application/pdf\r\n\r\n" +
            "%PDF-1.4\nsecond file\n%%EOF\r\n";
        String[] duplicates = {
            field("resourceType", "STUDENT_PROFILE"),
            field("resourceId", id(6)),
            field("purpose", "CV"),
            duplicateFile
        };
        String[] names = {"resourceType", "resourceId", "purpose", "file"};

        for (int i = 0; i < duplicates.length; i++) {
            String operationKey = key();
            var denied = post(student, operationKey, "STUDENT_PROFILE", id(6), "CV",
                "cv.pdf", "application/pdf", PDF, true, duplicates[i]);
            assertError(denied, 400, "SCHEMA_VALIDATION");
            assertEquals(objectsBefore, objects(), "No storage bytes after duplicate " + names[i]);
            assertEquals(filesBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM files", Integer.class), "No metadata after duplicate " + names[i]);
            assertEquals(bindingsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_bindings", Integer.class), "No binding after duplicate " + names[i]);
            assertEquals(auditsBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class),
                "No success audit after duplicate " + names[i]);
            assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE idempotency_key=?",
                Integer.class, operationKey), "No idempotency ledger after duplicate " + names[i]);
        }
    }

    @Test void qa01T106MissingAndBlankIdempotencyKeyCannotUploadPrivateFile() throws Exception {
        // QA01 TC06: A valid, authenticated multipart upload must reject a missing/blank
        // Idempotency-Key before writing metadata, storage, bindings, audit, or ledger.
        // DEV already checks a malformed non-UUID key; these cover two remaining inputs.
        var student = login("student");
        long beforeObjects = objects();
        int beforeFiles = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int beforeBindings = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        int beforeAudits = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);
        int beforeOperations = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class);

        var multipart = new ByteArrayOutputStream();
        multipart.write((field("resourceType", "STUDENT_PROFILE")
            + field("resourceId", id(6))
            + field("purpose", "CV")
            + "--" + BOUNDARY + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"cv.pdf\"\r\n"
            + "Content-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        multipart.write(PDF);
        multipart.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        byte[] requestBytes = multipart.toByteArray();

        for (boolean missingHeader : new boolean[] {true, false}) {
            String scenario = missingHeader ? "missing Idempotency-Key" : "blank Idempotency-Key";
            var request = HttpRequest.newBuilder(uri("/files"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .header(student.header(), student.token());
            if (!missingHeader) request.header("Idempotency-Key", "");
            var denied = student.client().send(
                request.POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
            // Missing @RequestHeader can be rejected by Spring MVC before the controller.
            // Assert the contract status without assuming the same error body at both layers.
            assertEquals(400, denied.statusCode(), scenario + ": " + body(denied));
            assertEquals(beforeObjects, objects(), "No stored object after " + scenario);
            assertEquals(beforeFiles, jdbc.queryForObject(
                "SELECT COUNT(*) FROM files", Integer.class), "No file row after " + scenario);
            assertEquals(beforeBindings, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_bindings", Integer.class), "No binding after " + scenario);
            assertEquals(beforeAudits, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class),
                "No successful upload audit after " + scenario);
            assertEquals(beforeOperations, jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class),
                "No upload operation record after " + scenario);
        }
    }

    @Test void qa01T106ValidPngAndJpegUploadReturnPendingMetadata() throws Exception {
        // QA01 T1-06-TC08: real ImageIO-encoded PNG/JPEG (not merely renamed PDF bytes).
        // CLEAN is not asserted: uploaded files must remain PENDING without an AV scanner.
        var student = login("student");
        int beforeFiles = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int beforeBindings = jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class);
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x00FF0000);
        image.setRGB(1, 0, 0x0000FF00);
        image.setRGB(0, 1, 0x000000FF);
        image.setRGB(1, 1, 0x00FFFFFF);

        for (String format : List.of("png", "jpeg")) {
            var encoded = new ByteArrayOutputStream();
            assertTrue(javax.imageio.ImageIO.write(image, format, encoded),
                "ImageIO encoder is unavailable for " + format);
            byte[] payload = encoded.toByteArray();
            String type = format.equals("png") ? "image/png" : "image/jpeg";
            String filename = format.equals("png") ? "proof.png" : "proof.jpg";

            var created = post(student, key(), "STUDENT_PROFILE", id(6), "EVIDENCE",
                filename, type, payload, true, "");
            assertEquals(202, created.statusCode(), format + ": " + body(created));
            var dto = json(created);
            String fileId = dto.get("fileId").asString();
            assertEquals(type, dto.get("contentType").asString());
            assertEquals(filename, dto.get("originalName").asString());
            assertEquals("PENDING", dto.get("scanStatus").asString());
            assertEquals("EVIDENCE", dto.get("purpose").asString());
            assertEquals(payload.length, dto.get("sizeBytes").asInt());
            assertFalse(body(created).contains("storageKey"));
            assertFalse(body(created).contains(ROOT.toString()));

            var metadata = get(student, fileId, "metadata", null);
            assertEquals(200, metadata.statusCode(), format + ": " + body(metadata));
            assertEquals(type, json(metadata).get("contentType").asString());
            assertEquals("PENDING", json(metadata).get("scanStatus").asString());
            privateHeaders(metadata);
            assertError(get(student, fileId, "content", null), 409, "FILE_NOT_CLEAN");
            assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM file_bindings WHERE file_id=?", Integer.class, fileId));
        }
        assertEquals(beforeFiles + 2,
            jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(beforeBindings + 2,
            jdbc.queryForObject("SELECT COUNT(*) FROM file_bindings", Integer.class));
    }

    @Test void qa01T106OwnerCanReadPendingAndRejectedMetadataWithoutContentAccess() throws Exception {
        // QA01 T1-06-TC12: REJECTED is a synthetic test state, not an actual AV scan.
        var student = login("student");
        var other = login("intern");
        var response = post(student, key(), "STUDENT_PROFILE", id(6), "CV",
            "cv.pdf", "application/pdf", PDF, true, "");
        assertEquals(202, response.statusCode(), body(response));
        String fileId = json(response).get("fileId").asString();
        int beforeDownloads = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
            Integer.class, fileId);

        var pending = get(student, fileId, "metadata", null);
        assertEquals(200, pending.statusCode(), body(pending));
        assertEquals(fileId, json(pending).get("fileId").asString());
        assertEquals("PENDING", json(pending).get("scanStatus").asString());
        assertEquals("CV", json(pending).get("purpose").asString());
        assertEquals("application/pdf", json(pending).get("contentType").asString());
        assertFalse(body(pending).contains("storageKey"));
        assertFalse(body(pending).contains(ROOT.toString()));
        privateHeaders(pending);
        assertError(get(student, fileId, "content", null), 409, "FILE_NOT_CLEAN");
        assertError(get(other, fileId, "metadata", null), 404, "RESOURCE_NOT_FOUND");

        int changed = jdbc.update(
            "UPDATE files SET scan_status='REJECTED' WHERE id=? AND scan_status='PENDING'", fileId);
        assertEquals(1, changed, "The fixture must transition from PENDING to REJECTED");
        try {
            var rejected = get(student, fileId, "metadata", null);
            assertEquals(200, rejected.statusCode(), body(rejected));
            assertEquals(fileId, json(rejected).get("fileId").asString());
            assertEquals("REJECTED", json(rejected).get("scanStatus").asString());
            assertEquals("CV", json(rejected).get("purpose").asString());
            assertFalse(body(rejected).contains("storageKey"));
            assertFalse(body(rejected).contains(ROOT.toString()));
            privateHeaders(rejected);
            assertError(get(student, fileId, "content", null), 409, "FILE_NOT_CLEAN");
            assertError(get(other, fileId, "metadata", null), 404, "RESOURCE_NOT_FOUND");
            assertEquals(beforeDownloads, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
                Integer.class, fileId));
        } finally {
            jdbc.update("UPDATE files SET scan_status='PENDING' WHERE id=? AND scan_status='REJECTED'", fileId);
        }
    }

    // QA01 T1-06-TC10: exact 10 MiB profile upload is allowed; 10 MiB + 1 byte is rejected.
    @Test void qa01T106ExactTenMiBProfileUploadAcceptedAndOverLimitRejected() throws Exception {
        final int limit = 10 * 1024 * 1024;
        Session student = login("student");
        long beforeObjects = objects();
        int beforeFiles = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int beforeAudits = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);
        int beforeLedgers = jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class);

        byte[] exactLimit = Arrays.copyOf(PDF, limit);
        HttpResponse<byte[]> accepted = post(student, key(), "STUDENT_PROFILE", id(6), "CV",
            "boundary.pdf", "application/pdf", exactLimit, true, "");
        assertEquals(202, accepted.statusCode(), body(accepted));
        JsonNode dto = json(accepted);
        assertEquals(limit, dto.get("sizeBytes").asInt());
        assertEquals("PENDING", dto.get("scanStatus").asString());
        String fileId = dto.get("fileId").asString();
        assertEquals(200, get(student, fileId, "metadata", null).statusCode());
        assertEquals(409, get(student, fileId, "content", null).statusCode());
        assertEquals(beforeObjects + 1, objects());
        assertEquals(beforeFiles + 1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(beforeAudits + 1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class));
        assertEquals(beforeLedgers + 1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class));

        byte[] overLimit = Arrays.copyOf(PDF, limit + 1);
        HttpResponse<byte[]> rejected = post(student, key(), "STUDENT_PROFILE", id(6), "CV",
            "over-boundary.pdf", "application/pdf", overLimit, true, "");
        assertError(rejected, 413, "FILE_TOO_LARGE");
        assertEquals(beforeObjects + 1, objects(), "Rejected upload must not create a storage object");
        assertEquals(beforeFiles + 1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(beforeAudits + 1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class));
        assertEquals(beforeLedgers + 1, jdbc.queryForObject("SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class));
    }

    // QA01 T1-06-TC11: boundary validation for an active student Task assignment.
    // The reverse-proxy >21 MiB response is separately covered by FileProxyIT.
    @Test void qa01T106TaskPdfExactTwentyMiBAcceptedAndOverLimitRejected() throws Exception {
        final int limit = 20 * 1024 * 1024;
        var mentor = as(5);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.addHeader("X-Context-Assignment-Id", workspace.assignments(mentor).stream()
            .filter(a -> a.kind().equals("MENTOR")).findFirst().orElseThrow().id());
        org.springframework.web.context.request.RequestContextHolder.setRequestAttributes(
            new org.springframework.web.context.request.ServletRequestAttributes(request));

        String group = jdbc.queryForObject("SELECT id FROM internship_groups LIMIT 1", String.class);
        String caseId = jdbc.queryForObject("SELECT id FROM internship_cases LIMIT 1", String.class);
        var task = workspace.createTask(mentor, key(),
            new vn.utbridora.auth.modules.internship.api.TaskDtos.CreateTask(
                UUID.fromString(group), "QA01 T1-06 TC11 20 MiB boundary",
                "Synthetic task for upload boundary; not real placement evidence",
                Instant.now().plusSeconds(3600),
                vn.utbridora.auth.modules.internship.api.TaskDtos.TaskPriority.NORMAL,
                List.of(UUID.fromString(caseId)), true));
        String assignment = task.assignments().getFirst().assignmentId().toString();
        var resource = new ResourceRef(ResourceType.TASK_ASSIGNMENT, assignment);
        var intern = as(7);
        taskActions.start(intern, assignment, key(),
            new vn.utbridora.auth.modules.internship.infrastructure.WorkspaceTaskActions.StartAssignment(1L, "IN_PROGRESS"));

        long beforeObjects = objects();
        int beforeFiles = jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class);
        int beforeAudits = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class);
        int beforeLedgers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class);

        byte[] exactPdf = Arrays.copyOf(PDF, limit);
        var accepted = files.upload(intern, key(), resource, FilePurpose.SUBMISSION, part(exactPdf));
        assertEquals(limit, accepted.sizeBytes());
        assertEquals(FilePurpose.SUBMISSION, accepted.purpose());
        assertEquals(resource, accepted.resource());
        assertEquals(FileMetadata.ScanStatus.PENDING, accepted.scanStatus());
        assertEquals(beforeObjects + 1, objects());
        assertEquals(beforeFiles + 1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(beforeAudits + 1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class));
        assertEquals(beforeLedgers + 1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class));

        byte[] overPdf = Arrays.copyOf(PDF, limit + 1);
        var denied = assertThrows(OperationFailure.class,
            () -> files.upload(intern, key(), resource, FilePurpose.SUBMISSION, part(overPdf)));
        assertEquals(413, denied.status);
        assertEquals(beforeObjects + 1, objects(), "Oversized upload must not create storage object");
        assertEquals(beforeFiles + 1, jdbc.queryForObject("SELECT COUNT(*) FROM files", Integer.class));
        assertEquals(beforeAudits + 1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_UPLOADED'", Integer.class));
        assertEquals(beforeLedgers + 1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM operation_records WHERE operation_code='FILE_UPLOAD'", Integer.class));
    }

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
    @Test void qa01T106EnterpriseMembershipRevocationHidesPrivateFile() throws Exception {
        // QA01: a formerly authorized HR session must lose access to a private
        // enterprise verification file when its organization membership is revoked.
        String verification = jdbc.queryForObject(
            "SELECT id FROM enterprise_verifications WHERE enterprise_org_id=?",
            String.class, id(12));
        String priorState = jdbc.queryForObject(
            "SELECT state FROM enterprise_verifications WHERE id=?",
            String.class, verification);
        String hrId = jdbc.queryForObject(
            "SELECT id FROM users WHERE email=?", String.class, "hr@example.test");
        List<String> activeMemberships = jdbc.queryForList(
            "SELECT id FROM memberships WHERE user_id=? AND active=true",
            String.class, hrId);
        assertFalse(activeMemberships.isEmpty(), "HR fixture requires an active membership");

        jdbc.update("UPDATE enterprise_verifications SET state='DRAFT' WHERE id=?", verification);
        try {
            Session hr = login("hr");
            var created = post(hr, key(), "ENTERPRISE_VERIFICATION", verification,
                "VERIFICATION", "evidence.pdf", "application/pdf", PDF, true, "");
            assertEquals(202, created.statusCode(), body(created));
            String fileId = json(created).get("fileId").asString();
            syntheticClean(fileId); // Test fixture only, not real antivirus evidence.

            assertEquals(200, get(hr, fileId, "metadata", null).statusCode());
            assertEquals(200, get(hr, fileId, "content", null).statusCode());
            int downloadedBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
                Integer.class, fileId);

            jdbc.update("UPDATE memberships SET active=false WHERE user_id=? AND active=true", hrId);
            assertError(get(hr, fileId, "metadata", null), 404, "RESOURCE_NOT_FOUND");
            assertError(get(hr, fileId, "content", "bytes=0-1"), 404, "RESOURCE_NOT_FOUND");
            assertEquals(downloadedBefore, jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
                Integer.class, fileId), "Denied download must not create a success audit");
        } finally {
            for (String membershipId : activeMemberships) {
                jdbc.update("UPDATE memberships SET active=true WHERE id=?", membershipId);
            }
            jdbc.update("UPDATE enterprise_verifications SET state=? WHERE id=?", priorState, verification);
        }
    }

    @Test void qa01T106SameEnterpriseColleagueCannotReadPrivateVerificationFile() throws Exception {
        // QA01 TC14: two distinct users with active ENTERPRISE rights in the same organization
        // do not automatically share access to a private verification file.
        String enterpriseId = id(12);
        String verificationId = jdbc.queryForObject(
            "SELECT id FROM enterprise_verifications WHERE enterprise_org_id=?",
            String.class, enterpriseId);
        String originalVerificationState = jdbc.queryForObject(
            "SELECT state FROM enterprise_verifications WHERE id=?",
            String.class, verificationId);

        // Reuse a synthetic user as a second HR only inside this disposable Testcontainers DB.
        // Keep all original membership fields so the test can restore its fixture in finally.
        record OriginalMembership(String id, String organizationId, String role, boolean active) {}
        OriginalMembership originalMember = jdbc.query(
            "SELECT id,organization_id,role,active FROM memberships " +
            "WHERE user_id=? AND active=true ORDER BY id LIMIT 1",
            (rs, row) -> new OriginalMembership(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4)),
            id(5)).stream().findFirst().orElseThrow(
                () -> new AssertionError("Missing active membership for synthetic colleague fixture"));

        jdbc.update("UPDATE enterprise_verifications SET state='DRAFT' WHERE id=?", verificationId);
        try {
            Session ownerHr = login("hr");
            var upload = post(ownerHr, key(), "ENTERPRISE_VERIFICATION", verificationId,
                "VERIFICATION", "evidence.pdf", "application/pdf", PDF, true, "");
            assertEquals(202, upload.statusCode(), body(upload));
            String fileId = json(upload).get("fileId").asString();
            syntheticClean(fileId); // Test fixture only; NOT evidence of a working scanner.
            assertEquals(200, get(ownerHr, fileId, "metadata", null).statusCode());
            assertEquals(200, get(ownerHr, fileId, "content", null).statusCode());

            // Promote the other synthetic account to active ENTERPRISE membership in the SAME org.
            // This distinguishes owner-only file access from missing organizational permission.
            jdbc.update("UPDATE memberships SET organization_id=?,role='ENTERPRISE',active=true " +
                "WHERE id=?", enterpriseId, originalMember.id());
            try {
                var colleagueAuth = as(5);
                var resource = new ResourceRef(ResourceType.ENTERPRISE_VERIFICATION, verificationId);
                assertTrue(authorizer.authorize(
                    ActorContext.fromAuthenticatedWorkflow(colleagueAuth, null),
                    resource, FilePurpose.VERIFICATION, Action.DOWNLOAD, Instant.now()).allowed(),
                    "Colleague must have CURRENT enterprise resource/download rights; otherwise this is not a true same-org test");

                Session colleague = login("mentor"); // Synthetic account now has ENTERPRISE role in this org.
                int successfulDownloadsBefore = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
                    Integer.class, fileId);

                var metadata = get(colleague, fileId, "metadata", null);
                assertError(metadata, 404, "RESOURCE_NOT_FOUND");
                privateHeaders(metadata);
                var content = get(colleague, fileId, "content", "bytes=0-1");
                assertError(content, 404, "RESOURCE_NOT_FOUND");
                privateHeaders(content);
                assertFalse(body(content).contains("Synthetic transport fixture"),
                    "Denied same-enterprise colleague must not receive private bytes");
                assertEquals(successfulDownloadsBefore, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action='FILE_DOWNLOADED' AND resource_id=?",
                    Integer.class, fileId), "Denied reads must not generate a success audit");

                // Owner access must remain intact while the other user is denied.
                assertEquals(200, get(ownerHr, fileId, "metadata", null).statusCode());
            } finally {
                jdbc.update("UPDATE memberships SET organization_id=?,role=?,active=? WHERE id=?",
                    originalMember.organizationId(), originalMember.role(),
                    originalMember.active(), originalMember.id());
            }
        } finally {
            jdbc.update("UPDATE enterprise_verifications SET state=? WHERE id=?",
                originalVerificationState, verificationId);
        }
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
