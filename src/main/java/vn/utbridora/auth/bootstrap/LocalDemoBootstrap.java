package vn.utbridora.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.core.PasswordPolicy;
import vn.utbridora.auth.modules.cooperation.application.DemandService;
import vn.utbridora.auth.modules.cooperation.application.PlacementLifecyclePublisher;
import vn.utbridora.auth.modules.cooperation.domain.DemandJson;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;
import vn.utbridora.auth.modules.student.application.StudentProfileService;

/** Synthetic prerequisites for an opted-in local database, optionally preserving its sole bootstrap admin. */
@Configuration(proxyBeanMethods=false)
@Profile("local")
@ConditionalOnProperty(name="app.demo.local.enabled",havingValue="true")
public class LocalDemoBootstrap {
    private static final String FIXTURE="LOCAL_SYNTHETIC_V1";
    private static final String PREFIX="00000000-0000-4000-8000-";
    private static String id(int number) { return PREFIX+String.format(Locale.ROOT,"%012d",number); }

    @Bean @Order(100)
    CommandLineRunner seedLocalDemo(JdbcTemplate jdbc,TransactionTemplate tx,PasswordEncoder encoder,
            PlacementLifecyclePublisher publisher,PlacementLifecycleConsumer consumer,
            StudentProfileService profiles,DemandService demands,AuthorizationPolicy authority,
            @Value("${app.bootstrap.admin.email:}") String adminEmail,
            @Value("${app.bootstrap.admin.password:}") String adminPassword,
            @Value("${DEMO_PASSWORD:}") String demoPassword,
            @Value("${app.demo.local.allow-existing-admin:false}") boolean allowExistingAdmin) {
        return args->{
            if(!"utbridora".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
                throw new IllegalStateException("Local demo requires database utbridora; no data was seeded");
            jdbc.execute("CREATE TABLE IF NOT EXISTS local_demo_seed_receipts (fixture_id VARCHAR(64) PRIMARY KEY,state VARCHAR(16) NOT NULL,created_at DATETIME(6) NOT NULL)");
            String sql=new ClassPathResource("demo/local.sql").getContentAsString(StandardCharsets.UTF_8)
                .replaceAll("(?m)^--[^\\r\\n]*","");
            PlacementLifecycleEvent event=tx.execute(status->{
                // Serialize with other seed/admin startups, including AdminBootstrap.
                jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE",String.class);
                var receipt=jdbc.queryForList("SELECT state FROM local_demo_seed_receipts WHERE fixture_id=? FOR UPDATE",String.class,FIXTURE);
                if(!receipt.isEmpty() && receipt.getFirst().equals("DONE")) return null;
                if(receipt.isEmpty()) {
                    String email=adminEmail.trim().toLowerCase(Locale.ROOT);
                    if(!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
                        || Set.of("school@example.test","coordinator@example.test","hr@example.test","mentor@example.test","student@example.test","intern@example.test").contains(email))
                        throw new IllegalStateException("Configure a distinct local bootstrap admin email");
                    long userCount=jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class);
                    var existingAdmins=allowExistingAdmin && userCount==1 ? jdbc.queryForList(
                        "SELECT u.id FROM users u WHERE u.email=? AND u.status='ACTIVE' AND EXISTS "
                        +"(SELECT 1 FROM memberships m WHERE m.user_id=u.id AND m.organization_id IS NULL AND m.role='ADMIN' AND m.active=true)",String.class,email) : List.<String>of();
                    if((userCount!=0 && existingAdmins.size()!=1)
                        || jdbc.queryForObject("SELECT COUNT(*) FROM organizations",Long.class)!=0
                        || jdbc.queryForObject("SELECT COUNT(*) FROM skill_categories",Long.class)!=0)
                        throw new IllegalStateException("Local demo requires a new database or an explicitly allowed sole bootstrap admin; disable LOCAL_DEMO_ENABLED to keep existing data. No accounts or passwords were overwritten");
                    PasswordPolicy.requireValid(demoPassword);
                    String adminId=existingAdmins.isEmpty()?id(1):existingAdmins.getFirst();
                    if(existingAdmins.isEmpty()) {
                        PasswordPolicy.requireValid(adminPassword);
                        jdbc.update("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES (?,?,?,'DEMO Admin','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                            adminId,email,encoder.encode(adminPassword));
                        jdbc.update("INSERT INTO memberships(id,user_id,organization_id,role,created_at) VALUES (UUID(),?,NULL,'ADMIN',UTC_TIMESTAMP(6))",adminId);
                    }
                    var params=Map.of("adminId",adminId,"demoPasswordHash",encoder.encode(demoPassword));
                    var named=new NamedParameterJdbcTemplate(jdbc);
                    for(String statement:sql.split(";")) if(!statement.isBlank()) named.update(statement,params);
                    createExamples(profiles,demands,authority);
                    jdbc.update("INSERT INTO local_demo_seed_receipts VALUES (?,'SOURCE_READY',UTC_TIMESTAMP(6))",FIXTURE);
                }
                return publisher.publish(id(2),id(99));
            });
            if(event==null) return; // Completed seeds never reset passwords, profile edits or assignments.
            String operation=jdbc.queryForObject("SELECT delivery_operation_id FROM placement_outbox WHERE event_id=?",String.class,event.eventId());
            var result=consumer.consume(new TrustedEventContext("T5",operation,event.traceId()),event);
            if(!result.outcome().startsWith("ACK_")) throw new IllegalStateException("Local demo Placement delivery did not acknowledge; restart to resume");
            tx.executeWithoutResult(status->{
                String caseId=jdbc.queryForObject("SELECT id FROM internship_cases WHERE placement_id=?",String.class,id(99));
                if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=?",Integer.class,caseId)==0)
                    jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES (?,?,?)",id(61),caseId,id(62));
                jdbc.update("UPDATE local_demo_seed_receipts SET state='DONE' WHERE fixture_id=?",FIXTURE);
            });
        };
    }

    private static void createExamples(StudentProfileService profiles,DemandService demands,AuthorizationPolicy authority) {
        LocalDate start=LocalDate.now(ZoneOffset.UTC).plusDays(14),end=start.plusMonths(3);
        var body=new LinkedHashMap<String,Object>();
        body.put("expectedProfileVersion",0);body.put("termId",id(21));body.put("majorCode","SE");
        body.put("phone","0901234567");body.put("skillsDeclared",true);body.put("availabilityDeclared",true);
        body.put("availablePeriods",List.of(Map.of("startDate",start.toString(),"endDate",end.toString())));
        var skill=new LinkedHashMap<String,Object>();
        skill.put("skillCode","JAVA");skill.put("level",2);skill.put("yearsPractice",1);skill.put("note",null);
        body.put("skills",List.of(skill));
        for(String field:List.of("customSkills","orientationCodes","subjectCodes","projects","certificates")) body.put(field,List.of());
        body.put("cvFileId",null);
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var context=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        try {
            context.setAuthentication(authority.currentSubject(id(6)).orElseThrow());
            profiles.save(context.getAuthentication(),UUID.randomUUID().toString(),DemandJson.write(body));
            var criteria=new RequiredCriteria(List.of("SE"),List.of(new SkillCriterion("JAVA",2,null)),new DateRange(start,end));
            var demand=new DemandInput(id(11),id(21),"DEMO - Java Internship",3,
                "Synthetic internship demand for Java API development",null,null,
                start.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),criteria,List.of(),"school-demo-v1","skills-v1");
            context.setAuthentication(authority.currentSubject(id(4)).orElseThrow());
            demands.create(context.getAuthentication(),id(12),UUID.randomUUID().toString(),demand);
        } finally {org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
    }
}
