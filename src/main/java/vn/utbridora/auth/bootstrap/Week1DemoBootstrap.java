package vn.utbridora.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import java.util.Map;
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
import vn.utbridora.auth.contracts.placement.*;
import vn.utbridora.auth.modules.cooperation.application.PlacementLifecyclePublisher;
import vn.utbridora.auth.modules.internship.integration.PlacementLifecycleConsumer;

/** Explicit synthetic fixture import, ONLY into the separate empty demo database. Never run on local/prod. */
@Configuration(proxyBeanMethods=false)
@Profile("demo")
@ConditionalOnProperty(name="app.demo.week1.enabled",havingValue="true")
public class Week1DemoBootstrap {
    private static final String FIXTURE="WEEK1_SYNTHETIC_V1";
    private static final String PLACEMENT="00000000-0000-4000-8000-000000000099";
    @Bean @Order(100)
    CommandLineRunner seedWeek1Demo(JdbcTemplate jdbc,TransactionTemplate tx,PasswordEncoder encoder,
            PlacementLifecyclePublisher publisher,PlacementLifecycleConsumer consumer,
            @Value("${DEMO_PASSWORD:}") String password) {
        return args->{
            if(!"utbridora_week1_demo".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
                throw new IllegalStateException("Synthetic demo requires its dedicated database; refusing to modify this database");
            vn.utbridora.auth.core.PasswordPolicy.requireValid(password);
            jdbc.execute("CREATE TABLE IF NOT EXISTS demo_fixture_receipts (fixture_id VARCHAR(64) PRIMARY KEY,state VARCHAR(16) NOT NULL,created_at DATETIME(6) NOT NULL)");
            var receipt=jdbc.queryForList("SELECT state FROM demo_fixture_receipts WHERE fixture_id=?",String.class,FIXTURE);
            if(!receipt.isEmpty() && receipt.getFirst().equals("DONE")) return;
            String source=new ClassPathResource("demo/week1.sql").getContentAsString(StandardCharsets.UTF_8).replaceAll("(?m)^--[^\\r\\n]*","");
            PlacementLifecycleEvent event=tx.execute(status->{
                if(receipt.isEmpty()) {
                    if(jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class)!=0) throw new IllegalStateException("Demo database is not empty; refusing to overwrite existing data");
                    var named=new NamedParameterJdbcTemplate(jdbc);var params=Map.of("passwordHash",encoder.encode(password));
                    for(String statement:source.split(";")) if(!statement.isBlank()) named.update(statement,params);
                    jdbc.update("INSERT INTO demo_fixture_receipts VALUES (?,'SOURCE_READY',UTC_TIMESTAMP(6))",FIXTURE);
                }
                return publisher.publish("00000000-0000-4000-8000-000000000002",PLACEMENT);
            });
            // The T5 source/outbox has committed before T3 receives the message.
            String operation=jdbc.queryForObject("SELECT delivery_operation_id FROM placement_outbox WHERE event_id=?",String.class,event.eventId());
            var result=consumer.consume(new TrustedEventContext("T5",operation,event.traceId()),event);
            if(!result.outcome().startsWith("ACK_")) throw new IllegalStateException("Synthetic Placement delivery did not acknowledge");
            tx.executeWithoutResult(status->{
                String caseId=jdbc.queryForObject("SELECT id FROM internship_cases WHERE placement_id=?",String.class,PLACEMENT);
                if(jdbc.queryForObject("SELECT COUNT(*) FROM internship_cohort_cases WHERE case_id=?",Integer.class,caseId)==0)
                    jdbc.update("INSERT INTO internship_cohort_cases(cohort_id,case_id,group_id) VALUES ('00000000-0000-4000-8000-000000000061',?,'00000000-0000-4000-8000-000000000062')",caseId);
                jdbc.update("UPDATE demo_fixture_receipts SET state='DONE' WHERE fixture_id=?",FIXTURE);
            });
        };
    }
}
