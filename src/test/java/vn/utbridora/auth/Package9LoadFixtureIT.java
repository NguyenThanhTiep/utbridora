package vn.utbridora.auth;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Functional fixture checks only. Hosted-runner timings never certify4vCPU/8GB R31. */
class Package9LoadFixtureIT {
    @Test void preparesFiveThousandProfilesAndRealFourHundredAndFiveThousandMatchingApisWithoutCallingThisALoadPass() throws Exception {
        Path root=Files.createTempDirectory("utbridora-load-fixture-it-");String schema=Package9OperationsIT.schema(),password="Synthetic-load-test-password-123";
        try(var mysql=new MySQLContainer<>("mysql:8.4").withDatabaseName(schema).withUsername("root").withPassword(UUID.randomUUID().toString())){
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load().migrate();String server;
            try(var c=Package9OperationsIT.connection(mysql,schema);var s=c.createStatement();var r=s.executeQuery("SELECT @@server_uuid")){assertTrue(r.next());server=r.getString(1);}
            var cfg=Package9OperationsIT.config(root.resolve("fixture"),schema,UUID.randomUUID().toString(),server,mysql);
            Path path=root.resolve("config.json"),seed=root.resolve("seed.json");Package9OperationsIT.write(path,cfg);
            var env=new HashMap<String,String>();env.put("UTB_OPS_DB_PASSWORD",mysql.getPassword());env.put("UTB_OPS_LOAD_PASSWORD_HASH",new BCryptPasswordEncoder().encode(password));
            run(env,"scripts/ops/load_fixture.py","--config",path.toString(),"--confirm-scope",cfg.get("scopeId").toString(),"--start-date",LocalDate.now().plusDays(14).toString(),"--end-date",LocalDate.now().plusDays(90).toString(),"--output",seed.toString());
            assertEquals(5000,Package9OperationsIT.count(mysql,schema,"student_profiles"));assertEquals(50000,Package9OperationsIT.count(mysql,schema,"student_profile_skills"));assertEquals(101,Package9OperationsIT.count(mysql,schema,"organizations"));
            // Rerun refuses existing records and must not reset any identity/password/profile.
            var before=Files.readString(seed);
            int repeat=outcome(env,"scripts/ops/load_fixture.py","--config",path.toString(),"--confirm-scope",cfg.get("scopeId").toString(),"--start-date",LocalDate.now().plusDays(14).toString(),"--end-date",LocalDate.now().plusDays(90).toString(),"--output",root.resolve("seed-repeat.json").toString());
            assertNotEquals(0,repeat);assertEquals(5000,Package9OperationsIT.count(mysql,schema,"student_profiles"));assertFalse(Files.exists(root.resolve("seed-repeat.json")));
            try(var context=new SpringApplicationBuilder(UtBridoraApplication.class).run(
                "--server.port=0","--spring.datasource.url="+mysql.getJdbcUrl(),"--spring.datasource.username="+mysql.getUsername(),"--spring.datasource.password="+mysql.getPassword(),
                "--app.bootstrap.admin.email=","--app.bootstrap.admin.password=","--app.demo.local.enabled=false","--app.t5.placement-delivery.enabled=false","--app.files.scan.enabled=false")){
                int port=context.getEnvironment().getRequiredProperty("local.server.port",Integer.class);
                env.put("UTB_LOAD_EMAIL_0","school0@load.invalid");env.put("UTB_LOAD_HR_EMAIL","hr@load.invalid");env.put("UTB_LOAD_PASSWORD",password);
                Path prepared=root.resolve("prepared.json");run(env,"scripts/ops/prepare_load.py","--seed",seed.toString(),"--base-url","http://127.0.0.1:"+port,
                    "--commit",System.getenv().getOrDefault("GITHUB_SHA","0000000000000000000000000000000000000000"),"--output",prepared.toString());
                var report=Package9OperationsIT.JSON.readTree(Files.readString(prepared));assertEquals("PREPARED_NOT_MEASURED",report.get("status").asString());assertEquals(2,report.get("fixtureChecks").size());
                Files.createDirectories(Path.of("target/package9"));
                // No seed dump, credentials or local paths in public CI evidence.
                Package9OperationsIT.write(Path.of("target/package9/load-fixture-verification.json"),Map.of("status","FUNCTIONAL_PASS_NOT_LOAD","commit",System.getenv().getOrDefault("GITHUB_SHA","LOCAL_UNVERIFIED"),
                    "profiles",5000,"enterprises",100,"skillRelations",50000,"checks",report.get("fixtureChecks"),"loadMeasured",false,"limits",List.of("Hosted runner is not fixed4vCPU/8GB SSD","Actual100-run concurrency benchmarks remain NOT_RUN")));
            }
            assertEquals(before,Files.readString(seed));
        } finally {try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    static void run(Map<String,String> env,String...args)throws Exception{
        assertEquals(0,outcome(env,args),"Fixture operation should succeed");
    }
    static int outcome(Map<String,String> env,String...args)throws Exception{
        var command=new ArrayList<>(List.of(System.getenv().getOrDefault("PYTHON","python3")));command.addAll(List.of(args));
        var b=new ProcessBuilder(command).redirectErrorStream(true);b.environment().putAll(env);var p=b.start();String log=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        int code=p.waitFor();if(code!=0)System.out.println(log);return code;
    }
}
