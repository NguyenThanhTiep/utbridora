package vn.utbridora.auth.modules.internship.api;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.testcontainers.containers.MySQLContainer;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.user.*;
import static org.junit.jupiter.api.Assertions.*;

/** DEV07 real browser acceptance for rubric, publication and private student results.
 * Never replaces a provider or connects to a developer/shared database. ACTIVE fixtures are
 * explicit synthetic prerequisites, not evidence of T5 approval/activation workflows.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "app.bootstrap.admin.email=","app.bootstrap.admin.password=","app.demo.local.enabled=false","app.t5.placement-delivery.enabled=false",
    "logging.level.org.springframework.jdbc.core=INFO"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class T312EvaluationBrowserIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    static final java.nio.file.Path FILE_ROOT=temporaryFiles();
    static java.nio.file.Path temporaryFiles() {
        try { return java.nio.file.Files.createTempDirectory("t312-evidence-"); }
        catch(java.io.IOException error) { throw new ExceptionInInitializerError(error); }
    }
    static {MYSQL.start();} // PER_CLASS Spring injection needs the mapped port before @BeforeAll.
    @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
        p.add("app.files.storage-root",()->FILE_ROOT.toString());
        p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;@Autowired PasswordEncoder encoder;
    @Autowired UserRepository users;@Autowired OrganizationRepository organizations;@Autowired MembershipRepository memberships;
    final JsonMapper json=JsonMapper.builder().build();final String password="Synthetic-T312-"+UUID.randomUUID();String hash;
    String school,enterprise,term,caseId,placement,cohort,group,task,assignment,mentorGrant,hrGrant,template;
    UserAccount admin,staff,coordinator,hr,mentor,student,outsider;
    @BeforeAll void passwordHash(){hash=encoder.encode(password);}
    @BeforeEach void fixture() throws Exception {
        assertTrue(MYSQL.isRunning()); // Every write below targets this private ephemeral container.
        school=org(Organization.Type.UNIVERSITY);enterprise=org(Organization.Type.ENTERPRISE);term=id();
        admin=user(Role.ADMIN,null);staff=user(Role.SCHOOL_STAFF,school);coordinator=user(Role.ENTERPRISE,enterprise);
        hr=user(Role.ENTERPRISE,enterprise);mentor=user(Role.ENTERPRISE,enterprise);student=user(Role.STUDENT,school);
        outsider=user(Role.STUDENT,org(Organization.Type.UNIVERSITY));
        jdbc.update("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,?,'2020-01-01','2099-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school,"SYNTHETIC-"+id());
        String delegation=id();
        jdbc.update("INSERT INTO school_delegations(id,user_id,school_org_id,term_id,granted_by,decision_ref,profile_read_fields_json,valid_from,valid_until,active,version) VALUES (?,?,?,?,?,'SYNTHETIC_T312_PREREQUISITE',JSON_ARRAY(),DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),true,1)",delegation,staff.id,school,term,admin.id);
        jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,'t3:coordinator:confirm')",delegation);
        jdbc.update("INSERT INTO enterprise_verifications(id,enterprise_org_id,school_org_id,state,submission_no,registration_code,registered_name,address,contact_name,contact_email,contact_phone,evidence_file_ids_json,missing_fields_json,decision_ref,submitter_id,submitted_at,reviewer_id,reviewed_at,version,created_at,updated_at) VALUES (?,?,?,'APPROVED',1,'SYNTHETIC','Synthetic enterprise','Synthetic address','Synthetic contact','synthetic@example.test','0901234567',JSON_ARRAY(),JSON_ARRAY(),'SYNTHETIC_T312_PREREQUISITE',?,UTC_TIMESTAMP(6),?,UTC_TIMESTAMP(6),1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",id(),enterprise,school,coordinator.id,staff.id);
        String parent=grant("COORDINATOR",coordinator.id,staff.id,null,delegation,null);
        hrGrant=grant("HR",hr.id,coordinator.id,parent,null,null);
        placement=id();caseId=id();cohort=id();group=id();task=id();assignment=id();
        jdbc.update("INSERT INTO placements(id,student_user_id,school_org_id,enterprise_org_id,term_id,demand_id,cooperation_id,proposal_item_id,state,version,effective_at,start_date,end_date,approved_at,decision_ref) VALUES (?,?,?,?,?,?,?,?,'ACTIVE',2,DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 DAY),'2020-01-01','2099-01-01',DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'SYNTHETIC_T312_PREREQUISITE')",placement,student.id,school,enterprise,term,id(),id(),id());
        jdbc.update("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',2,2)",caseId,placement,student.id,school,enterprise,term);
        jdbc.update("INSERT INTO internship_cohorts(id,enterprise_org_id,school_org_id,term_id,code,name,version) VALUES (?,?,?,?,?,'Synthetic T312',1)",cohort,enterprise,school,term,"T312-"+id());
        jdbc.update("INSERT INTO internship_groups(id,cohort_id,code,version) VALUES (?,?,'G1',1)",group,cohort);
        jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",cohort,caseId,group);
        // Week 5 regression: a published grade does not require a Task to exist.
        mentorGrant=grant("MENTOR",mentor.id,hr.id,hrGrant,null,group);
        for(String permission:List.of("t3:grades:publish","t3:result_policy:manage","t3:workspace:read"))
            jdbc.update("INSERT INTO school_delegation_permissions(assignment_id,permission_code) VALUES (?,?)",delegation,permission);
        template=id();
        jdbc.update("INSERT INTO internship_rubric_templates(id,template_version,school_org_id,term_id,template_json) VALUES (?,1,?,?,?)",
            template,school,term,json.writeValueAsString(Map.of("templateId",template,"templateVersion",1,"criteria",List.of(
                Map.of("criterionCode","QUALITY","label","Quality","weight",40),Map.of("criterionCode","PROCESS","label","Process","weight",60)))));
    }
    private String grant(String kind,String subject,String grantor,String parent,String delegation,String assignedGroup) {
        String grant=id();
        jdbc.update("INSERT INTO internship_context_assignments(id,kind,user_id,enterprise_org_id,school_org_id,term_id,group_id,parent_assignment_id,school_delegation_id,granted_by,decision_ref,invitation_accepted,valid_from,valid_until) VALUES (?,?,?,?,?,?,?,?,?,?,'SYNTHETIC_T312_PREREQUISITE',true,DATE_SUB(UTC_DATE(),INTERVAL 1 HOUR),DATE_ADD(UTC_DATE(),INTERVAL 30 DAY))",
                grant,kind,subject,enterprise,school,term,assignedGroup,parent,delegation,grantor);return grant;
    }
    private String org(Organization.Type type) {var o=new Organization();o.name="Synthetic "+id();o.type=type;o.status=Organization.Status.VERIFIED;return organizations.saveAndFlush(o).id;}
    private UserAccount user(Role role,String org) {
        var u=new UserAccount();u.email=id()+"@example.test";u.displayName="Synthetic";u.passwordHash=hash;u.status=AccountStatus.ACTIVE;users.saveAndFlush(u);
        var m=new Membership();m.userId=u.id;m.organizationId=org;m.role=role;memberships.saveAndFlush(m);return u;
    }
    private static String id(){return UUID.randomUUID().toString();}
    private Map<String,String> browserConfig() {
        var config=new LinkedHashMap<String,String>();
        config.put("password",password);config.put("studentEmail",student.email);config.put("mentorEmail",mentor.email);
        config.put("hrEmail",hr.email);config.put("schoolEmail",staff.email);config.put("outsiderEmail",outsider.email);
        config.put("mentorContext",mentorGrant);config.put("hrContext",hrGrant);config.put("schoolId",school);
        config.put("enterpriseId",enterprise);config.put("termId",term);config.put("caseId",caseId);config.put("templateId",template);
        return config;
    }
    private void runBrowserSpec(String spec,String report,String log) throws Exception {
        boolean windows=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        var builder=windows?new ProcessBuilder("cmd.exe","/c","npm.cmd run test:e2e -- "+spec):
            new ProcessBuilder("npm","run","test:e2e","--",spec);
        builder.directory(new java.io.File("web-demo"));var env=builder.environment();
        env.put("API_PROXY_TARGET","http://localhost:"+port);env.put("E2E_REPORT_FILE",report);
        env.put("E2E_T312_CONFIG",json.writeValueAsString(browserConfig()));
        builder.redirectErrorStream(true).redirectOutput(new java.io.File(log));
        var process=builder.start();try {
            if(!process.waitFor(300,TimeUnit.SECONDS))fail("Browser timed out: "+log);
            assertEquals(0,process.exitValue(),"See "+log);
        } finally {if(process.isAlive())process.destroyForcibly();}
    }
    @Test void fourRolesUseRealEvaluationPublicationAndResultApis() throws Exception {
        runBrowserSpec("internship-week4-runtime.spec.ts","../target/t3-12-runtime-browser.xml","target/t3-12-runtime-browser.log");
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluations WHERE case_id=?",Integer.class,caseId));
        String evaluation=jdbc.queryForObject("SELECT id FROM internship_evaluations WHERE case_id=?",String.class,caseId);
        assertEquals("76.00",jdbc.queryForObject("SELECT total100 FROM internship_evaluation_versions WHERE evaluation_id=? AND version=1",java.math.BigDecimal.class,evaluation).toPlainString());
        assertEquals("20.00",jdbc.queryForObject("SELECT v.total100 FROM internship_evaluation_versions v JOIN internship_evaluations e ON e.id=v.evaluation_id AND e.published_version=v.version WHERE e.id=?",java.math.BigDecimal.class,evaluation).toPlainString());
        assertEquals("DRAFT",jdbc.queryForObject("SELECT v.state FROM internship_evaluation_versions v JOIN internship_evaluations e ON e.id=v.evaluation_id AND e.current_version=v.version WHERE e.id=?",String.class,evaluation));
    }
    @Test void qa03VerifiesRubricScorePublicationGateAndImmutableHistory() throws Exception {
        runBrowserSpec("t3-12-qa03.spec.ts","../target/t3-12-qa03-runtime.xml","target/t3-12-qa03-runtime.log");
        String evaluation=jdbc.queryForObject("SELECT id FROM internship_evaluations WHERE case_id=?",String.class,caseId);
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluation_versions WHERE evaluation_id=?",Long.class,evaluation)>=2);
        assertEquals("76.00",jdbc.queryForObject("SELECT total100 FROM internship_evaluation_versions WHERE evaluation_id=? AND version=1",java.math.BigDecimal.class,evaluation).toPlainString());
        Long publishedVersion=jdbc.queryForObject("SELECT published_version FROM internship_evaluations WHERE id=?",Long.class,evaluation);
        assertTrue(publishedVersion>1);
        assertEquals("20.00",jdbc.queryForObject("SELECT total100 FROM internship_evaluation_versions WHERE evaluation_id=? AND version=?",java.math.BigDecimal.class,evaluation,publishedVersion).toPlainString());
        assertEquals(publishedVersion,jdbc.queryForObject("SELECT current_version FROM internship_evaluations WHERE id=?",Long.class,evaluation));
    }
}
