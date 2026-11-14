package vn.utbridora.auth;

import db.convergence.Week4Schema;
import db.migration.base.V40__converge_week4_file_and_grade_lineages;
import java.sql.*;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL upgrades, populated immutable data and negative preflight; no shared DB. */
class Week4ConvergenceIT {
    static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withUsername("root");
    static int sequence;String url,user,school,org,term,caseId,template,evaluation;List<String> files;
    @TempDir Path oldLayout;
    @BeforeAll static void start(){MYSQL.start();}
    @AfterAll static void stop(){MYSQL.stop();}
    @BeforeEach void database() throws Exception {
        String name="converge_"+(++sequence);
        try(var c=DriverManager.getConnection(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());var s=c.createStatement()){s.execute("CREATE DATABASE "+name);}
        url=MYSQL.getJdbcUrl().replace("/"+MYSQL.getDatabaseName(),"/"+name);
    }
    private Connection connection() throws SQLException{return DriverManager.getConnection(url,MYSQL.getUsername(),MYSQL.getPassword());}
    private Flyway flyway(boolean t1,String target){
        var b=Flyway.configure().dataSource(url,MYSQL.getUsername(),MYSQL.getPassword());
        if(t1)b.locations("classpath:db/migration/base","classpath:db/migration/main","classpath:db/legacy/t1-week4");
        if(target!=null)b.target(target);return b.load();
    }
    private Flyway historical(boolean t1,String target) throws Exception {
        Path source=Path.of("src/main/resources/db/migration");
        try(var stream=Files.walk(source)) {
            for(Path file:stream.filter(Files::isRegularFile).toList()) {
                String name=file.getFileName().toString();boolean week4=name.startsWith("V38__")||name.startsWith("V39__");
                if(t1&&week4)continue;
                Path destination=week4?oldLayout.resolve("base").resolve(name):oldLayout.resolve(source.relativize(file));
                Files.createDirectories(destination.getParent());Files.copy(file,destination,StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if(t1)Files.copy(Path.of("src/main/resources/db/legacy/t1-week4/V38__durable_private_file_scans.sql"),oldLayout.resolve("base/V38__durable_private_file_scans.sql"),StandardCopyOption.REPLACE_EXISTING);
        return Flyway.configure().dataSource(url,MYSQL.getUsername(),MYSQL.getPassword())
            .locations("filesystem:"+oldLayout.toAbsolutePath().toString().replace('\\','/'))
            .javaMigrations(new db.migration.base.V32__converge_week3_lineages()).target(target).load();
    }
    @ParameterizedTest @ValueSource(strings={"clean","main37","t3-38","t3-39","t1-38"})
    void upgradesPreserveDataAndHistory(String lineage) throws Exception {
        boolean t1=lineage.startsWith("t1");
        if(!lineage.equals("clean")) {
            flyway(t1,"37").migrate();seedBase();
            if(!lineage.equals("main37")) {
                historical(t1,lineage.endsWith("39")?"39":"38").migrate();
                if(t1)seedScanner();else seedEvaluation(lineage.endsWith("39"));
            }
        }
        var oldHistory=lineage.equals("clean")?Map.<String,String>of():history();
        var before=lineage.equals("clean")?Map.<String,String>of():data();
        var upgraded=flyway(t1,null);upgraded.migrate();upgraded.validate();assertEquals("40",upgraded.info().current().getVersion().getVersion());
        assertEquals(0,upgraded.migrate().migrationsExecuted);
        var after=history();oldHistory.forEach((key,value)->assertEquals(value,after.get(key),key));
        if(!lineage.equals("clean")){var current=data();before.forEach((key,value)->assertEquals(value,current.get(key),key));}
        try(var c=connection()) {
            String reference=V40__converge_week4_file_and_grade_lineages.resource("db/convergence/week4-shapes.txt");
            assertEquals(section(reference,"scanner"),Week4Schema.shape(c,List.of("files")));
            assertEquals(section(reference,"evaluation"),Week4Schema.shape(c,Week4Schema.EVALUATION));
            assertEquals(section(reference,"visibility"),Week4Schema.visibility(c));
        }
    }
    @ParameterizedTest @ValueSource(strings={"scanner-index","scanner-column","evaluation-fk","evaluation-type","visibility-ceiling","missing-family","unknown-family"})
    void malformedSchemaStopsBeforeConvergenceDdl(String damage) throws Exception {
        boolean t1=damage.startsWith("scanner");flyway(t1,"37").migrate();
        flyway(t1,t1?"38":"39").migrate();
        switch(damage) {
            case "scanner-index" -> sql("ALTER TABLE files DROP INDEX ix_file_scan_due");
            case "scanner-column" -> sql("ALTER TABLE files MODIFY scanner_reason_code VARCHAR(16) NULL");
            case "evaluation-fk" -> sql("ALTER TABLE internship_evaluations DROP FOREIGN KEY fk_evaluation_published");
            case "evaluation-type" -> sql("ALTER TABLE internship_evaluation_versions MODIFY total100 DECIMAL(6,2) NOT NULL");
            case "visibility-ceiling" -> sql("DELETE FROM role_permissions WHERE permission_code='t3:grades:publish'");
            case "missing-family" -> {
                sql("DROP TABLE internship_evaluation_revision_decisions");sql("DROP TABLE internship_school_result_policies");sql("DROP TABLE internship_grade_visibility");
                sql("DELETE FROM role_permissions WHERE permission_code IN ('t3:grades:publish','t3:result_policy:manage','t3:grade_visibility:manage')");
                sql("DELETE FROM permissions WHERE code IN ('t3:grades:publish','t3:result_policy:manage','t3:grade_visibility:manage')");
            }
            case "unknown-family" -> {try(var c=connection()){V40__converge_week4_file_and_grade_lineages.apply(c,V40__converge_week4_file_and_grade_lineages.SCANNER);}}
        }
        var beforeHistory=history();String before;
        try(var c=connection()){before=Week4Schema.shape(c,List.of("files"))+Week4Schema.shape(c,Week4Schema.EVALUATION)+Week4Schema.visibility(c);}
        var error=assertThrows(FlywayException.class,()->flyway(t1,null).migrate());
        assertTrue(error.getMessage().contains("Unknown or partial week 4 schema"),error.getMessage());
        try(var c=connection()){assertEquals(before,Week4Schema.shape(c,List.of("files"))+Week4Schema.shape(c,Week4Schema.EVALUATION)+Week4Schema.visibility(c));}
        var after=history();beforeHistory.forEach((key,value)->assertEquals(value,after.get(key)));
    }
    @Test void wrongHistoricalChecksumRemainsRejected() throws Exception {
        flyway(true,"38").migrate();var before=history();
        assertFalse(flyway(false,null).validateWithResult().validationSuccessful);assertEquals(before,history());
    }
    private static String section(String reference,String name){return reference.split("@@"+name+"\n",2)[1].split("\n@@",2)[0].stripTrailing();}
    private Map<String,String> history() throws Exception {
        var result=new TreeMap<String,String>();try(var c=connection();var s=c.createStatement();var r=s.executeQuery("SELECT installed_rank,version,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")){
            while(r.next())result.put(r.getString(1),r.getString(2)+"|"+r.getString(3)+"|"+r.getString(4)+"|"+r.getBoolean(5));}return result;
    }
    private Map<String,String> data() throws Exception {
        var result=new TreeMap<String,String>();
        result.put("files",query("SELECT id,storage_key,sha256,scan_status,classification FROM files ORDER BY id"));
        result.put("bindings",query("SELECT * FROM file_bindings ORDER BY id"));
        try(var c=connection()) {
            if(!Week4Schema.shape(c,Week4Schema.EVALUATION).isEmpty()) {
                for(String table:Week4Schema.EVALUATION)result.put(table,query("SELECT * FROM "+table+" ORDER BY 1,2"));
            }
            if(!Week4Schema.shape(c,Week4Schema.VISIBILITY).isEmpty())for(String table:Week4Schema.VISIBILITY)result.put(table,query("SELECT * FROM "+table+" ORDER BY 1,2"));
        }
        if(query("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='files' AND COLUMN_NAME='scan_attempt'").length()>0)
            result.put("scanner",query("SELECT id,scan_attempt,scanner_reason_code,scan_next_attempt_at,scan_lease_id,scan_lease_until,version FROM files ORDER BY id"));
        return result;
    }
    private String query(String sql) throws Exception {
        var out=new StringBuilder();try(var c=connection();var s=c.createStatement();var r=s.executeQuery(sql)) {
            while(r.next()){for(int i=1;i<=r.getMetaData().getColumnCount();i++)out.append(r.getString(i)).append('|');out.append('\n');}}
        return out.toString();
    }
    private void seedBase() throws Exception {
        user=id();school=id();org=id();term=id();caseId=id();template=id();evaluation=id();files=List.of(id(),id(),id());
        sql("INSERT INTO users(id,email,password_hash,display_name,status,created_at,updated_at) VALUES (?,'convergence@example.test','unusable','Synthetic','ACTIVE',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",user);
        sql("INSERT INTO organizations(id,name,type,status,created_at) VALUES (?,'School','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6)),(?,'Enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6))",school,org);
        sql("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES (?,?,'CONVERGE','2026-01-01','2027-01-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",term,school);
        sql("INSERT INTO internship_cases(id,placement_id,student_user_id,school_org_id,enterprise_org_id,term_id,state,version,last_applied_version) VALUES (?,?,?,?,?,?,'IN_PROGRESS',7,2)",caseId,id(),user,school,org,term);
        var states=List.of("PENDING","CLEAN","REJECTED");
        for(int i=0;i<3;i++)sql("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES (?,?,?,?,'synthetic.pdf','application/pdf',8,?,'PRIVATE',?,UTC_TIMESTAMP(6))",files.get(i),user,school,"synthetic/"+files.get(i),"a".repeat(64),states.get(i));
        sql("INSERT INTO file_bindings VALUES (?,?,'EVALUATION','EVIDENCE',?,UTC_TIMESTAMP(6))",id(),files.get(1),evaluation);
    }
    private void seedScanner() throws Exception {
        sql("UPDATE files SET scan_attempt=2,scanner_reason_code='RETRY',scan_next_attempt_at='2026-12-01 00:00:00',scan_lease_id=?,scan_lease_until='2026-12-01 00:01:00',version=3 WHERE id=?",id(),files.getFirst());
    }
    private void seedEvaluation(boolean policies) throws Exception {
        sql("INSERT INTO internship_rubric_templates VALUES (?,1,?,?,?)",template,school,term,"{\"immutable\":\"rubric\"}");
        sql("INSERT INTO internship_evaluations(id,case_id) VALUES (?,?)",evaluation,caseId);
        sql("INSERT INTO internship_evaluation_versions VALUES (?,1,1,'PUBLISHED',?,1,?,76.00,?,UTC_TIMESTAMP(6))",evaluation,template,"{\"immutable\":\"published\",\"total100\":76.00}",user);
        sql("UPDATE internship_evaluations SET current_version=1,published_version=1 WHERE id=?",evaluation);
        sql("INSERT INTO internship_evaluation_files VALUES (?,1,?)",evaluation,files.get(1));
        if(policies) {
            sql("INSERT INTO internship_evaluation_versions VALUES (?,2,2,'DRAFT',?,1,?,40.00,?,UTC_TIMESTAMP(6))",evaluation,template,"{\"immutable\":\"revision\"}",user);
            sql("UPDATE internship_evaluations SET current_version=2 WHERE id=?",evaluation);
            sql("INSERT INTO internship_evaluation_revision_decisions VALUES (?,2,'SYNTHETIC','CORRECTION')",evaluation);
            sql("INSERT INTO internship_school_result_policies VALUES (?,?,true,4)",school,term);
            sql("INSERT INTO internship_grade_visibility VALUES (?,?,'ORGANIZATION_TERM',NULL,true,2)",org,term);
            sql("INSERT INTO internship_grade_visibility VALUES (?,?,?,?,false,3)",org,term,caseId,caseId);
        }
    }
    private void sql(String sql,Object...args) throws Exception {try(var c=connection();var s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}}
    private static String id(){return UUID.randomUUID().toString();}
}
