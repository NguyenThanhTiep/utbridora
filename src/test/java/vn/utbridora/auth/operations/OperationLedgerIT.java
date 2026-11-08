package vn.utbridora.auth.operations;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL transactions and contention, independent of any module's fake operation store. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationLedgerIT {
    final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString());
    JdbcTemplate jdbc; TransactionTemplate transactions; OperationLedger ledger; String actor;
    public record Reply(String id,long version,String message) {}

    @BeforeAll void start() {
        mysql.start();
        Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load().migrate();
        var source=new DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());
        jdbc=new JdbcTemplate(source);transactions=new TransactionTemplate(new DataSourceTransactionManager(source));
        ledger=new OperationLedger(jdbc);
        jdbc.execute("CREATE TABLE ledger_test_effects (id VARCHAR(36) PRIMARY KEY, value INT NOT NULL)");
    }
    @AfterAll void stop() { mysql.stop(); }
    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM operation_records");jdbc.update("DELETE FROM audit_logs");
        jdbc.update("DELETE FROM ledger_test_effects");jdbc.update("DELETE FROM users");
        actor=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO users (id,email,password_hash,display_name,status,security_version,failed_attempts,created_at,updated_at) VALUES (?,?,'!NO_LOGIN!','Ledger test','ACTIVE',0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",actor,actor+"@example.test");
    }
    OperationLedger.Command command(String key,Object payload) { return new OperationLedger.Command(actor,null,"TEST_UPDATE",key,payload,200); }
    Reply write() {
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO ledger_test_effects VALUES (?,1)",id);
        jdbc.update("INSERT INTO audit_logs (id,actor_id,action,resource_type,resource_id,result,created_at) VALUES (?,?,'LOGIN','user',?,'SUCCESS',UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),actor,actor);
        return new Reply(id,1,"saved");
    }
    Reply run(OperationLedger.Command command,java.util.function.Supplier<Reply> work) {
        return transactions.execute(status->ledger.execute(command,Reply.class,work,Reply::id));
    }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class); }

    @Test void replaysOriginalResponseAndRejectsChangedPayloadWithoutRepeatingEffectOrAudit() {
        String key=UUID.randomUUID().toString();
        var original=run(command(key,Map.of("expectedVersion",0)),this::write);
        assertEquals(original,run(command(key,Map.of("expectedVersion",0)),()->{fail("Replay reran domain checks");return null;}));
        OperationFailure conflict=assertThrows(OperationFailure.class,()->run(command(key,Map.of("expectedVersion",1)),this::write));
        assertEquals("IDEMPOTENCY_KEY_REUSED",conflict.code);
        assertEquals(1,count("ledger_test_effects"));assertEquals(1,count("audit_logs"));assertEquals(1,count("operation_records"));
    }
    @Test void cacheExpiryDoesNotRepeatADurableEffectAndChangedPayloadStillConflicts() {
        String key=UUID.randomUUID().toString();run(command(key,Map.of("x",1)),this::write);
        jdbc.update("UPDATE operation_records SET response_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND");
        assertEquals("IDEMPOTENCY_RESPONSE_EXPIRED",assertThrows(OperationFailure.class,()->run(command(key,Map.of("x",1)),this::write)).code);
        assertEquals("IDEMPOTENCY_KEY_REUSED",assertThrows(OperationFailure.class,()->run(command(key,Map.of("x",2)),this::write)).code);
        assertEquals(1,count("ledger_test_effects"));
    }
    @Test void domainOrAuditFailureRollsBackReservationAndAllWritesThenRetryCanSucceed() {
        String key=UUID.randomUUID().toString();var command=command(key,Map.of("x",1));
        assertThrows(IllegalStateException.class,()->run(command,()->{write();throw new IllegalStateException("simulated failure");}));
        assertEquals(0,count("operation_records"));assertEquals(0,count("ledger_test_effects"));assertEquals(0,count("audit_logs"));
        assertNotNull(run(command,this::write));assertEquals(1,count("ledger_test_effects"));
    }
    @Test void concurrentSameKeyCommitsOneEffectAndTheWaiterReplaysIt() throws Exception {
        var command=command(UUID.randomUUID().toString(),Map.of("x",1));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var calls=new AtomicInteger();
        try (var pool=Executors.newFixedThreadPool(2)) {
            Future<Reply> first=pool.submit(()->run(command,()->{
                calls.incrementAndGet();entered.countDown();await(release);return write();
            }));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            Future<Reply> second=pool.submit(()->run(command,()->{calls.incrementAndGet();return write();}));
            release.countDown();assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,calls.get());assertEquals(1,count("operation_records"));assertEquals(1,count("audit_logs"));
        } finally { release.countDown(); }
    }
    @Test void competingDeliveryTimesOutSafelyWithoutASecondEffect() throws Exception {
        var command=command(UUID.randomUUID().toString(),Map.of("x",1));
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try (var pool=Executors.newFixedThreadPool(2)) {
            Future<Reply> first=pool.submit(()->run(command,()->{entered.countDown();await(release);return write();}));
            assertTrue(entered.await(10,TimeUnit.SECONDS));
            Future<OperationFailure> second=pool.submit(()->assertThrows(OperationFailure.class,()->run(command,this::write)));
            try {
                OperationFailure failure=second.get(12,TimeUnit.SECONDS);
                assertEquals("REQUEST_IN_PROGRESS",failure.code);assertEquals(1,failure.retryAfterSeconds);
            } finally { release.countDown(); }
            first.get(10,TimeUnit.SECONDS);assertEquals(1,count("ledger_test_effects"));
        } finally { release.countDown(); }
    }
    @Test void organizationScopesDoNotCollide() {
        String key=UUID.randomUUID().toString();
        var a=new OperationLedger.Command(actor,UUID.randomUUID().toString(),"TEST_UPDATE",key,Map.of("x",1),200);
        var b=new OperationLedger.Command(actor,UUID.randomUUID().toString(),"TEST_UPDATE",key,Map.of("x",1),200);
        assertNotEquals(run(a,this::write).id(),run(b,this::write).id());assertEquals(2,count("operation_records"));
    }
    @Test void purgedResponseStillDoesNotRepeatTheEffect() {
        String key=UUID.randomUUID().toString();var command=command(key,Map.of("x",1));
        run(command,this::write);
        jdbc.update("UPDATE operation_records SET response_json=NULL,response_expires_at=NULL");
        assertEquals("IDEMPOTENCY_RESPONSE_EXPIRED",assertThrows(OperationFailure.class,()->run(command,this::write)).code);
        assertEquals(1,count("ledger_test_effects"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"token","resetToken","passwordHash","credentials"})
    void secretResponseIsNotCachedAndItsEffectRollsBack(String field) {
        assertThrows(OperationFailure.class,()->transactions.execute(status->ledger.execute(command(UUID.randomUUID().toString(),Map.of("x",1)),Map.class,
                ()->{write();return Map.of(field,"not-a-real-secret");},value->null)));
        assertEquals(0,count("operation_records"));assertEquals(0,count("ledger_test_effects"));
    }
    @Test void requiresAnExistingDomainTransaction() {
        assertThrows(IllegalStateException.class,()->ledger.execute(command(UUID.randomUUID().toString(),Map.of("x",1)),Reply.class,this::write,Reply::id));
    }
    static void await(CountDownLatch latch) {
        try { if(!latch.await(15,TimeUnit.SECONDS)) throw new IllegalStateException("Test latch timed out"); }
        catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new IllegalStateException(interrupted); }
    }
}
