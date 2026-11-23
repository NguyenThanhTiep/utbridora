package vn.utbridora.auth;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.testcontainers.containers.MySQLContainer;

class MigrationUpgradeIT {
    @Test void onboardingBackfillPreservesTokenHistoryAndQuarantinesAmbiguousPendingIdentities() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("40").load().migrate();
            var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var jdbc=new org.springframework.jdbc.core.JdbcTemplate(ds);
            for(int i=0;i<4;i++) jdbc.update("INSERT INTO one_time_tokens(id,token_hash,purpose,email,invite_role,expires_at,consumed_at,created_at) VALUES(?,?,'INVITATION',?,'ADMIN',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 HOUR),?,UTC_TIMESTAMP(6))",UUID.randomUUID().toString(),String.format("%064d",i+1),i<2?"ambiguous@example.test":i==2?"single@example.test":"consumed@example.test",i==3?java.sql.Timestamp.from(java.time.Instant.now()):null);
            var before=jdbc.queryForList("SELECT id,token_hash,purpose,user_id,email,invite_role,organization_id,expires_at,consumed_at,created_at FROM one_time_tokens ORDER BY id");
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();assertEquals(4,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals("44",flyway.info().current().getVersion().getVersion());assertEquals(0,flyway.migrate().migrationsExecuted);
            assertEquals(before,jdbc.queryForList("SELECT id,token_hash,purpose,user_id,email,invite_role,organization_id,expires_at,consumed_at,created_at FROM one_time_tokens ORDER BY id"));
            assertEquals("REVIEW_REQUIRED",jdbc.queryForObject("SELECT claim_state FROM invitation_claims WHERE email_canonical='ambiguous@example.test'",String.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email='ambiguous@example.test' AND claim_id IS NOT NULL",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM one_time_tokens WHERE email='single@example.test' AND claim_id IS NOT NULL AND generation=1 AND issued_at=created_at",Integer.class));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM invitation_claims WHERE email_canonical='consumed@example.test'",Integer.class));
        }
    }
    @Test void privateFileUpgradePreservesLegacyMetadataBindingsAndScanState() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString())) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("27").load().migrate();
            String user=UUID.randomUUID().toString(),file=UUID.randomUUID().toString();
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users VALUES ('"+user+"','file-upgrade@example.test','unusable-test-hash','File Upgrade Test','ACTIVE',0,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO files(id,owner_user_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES ('"+file+"','"+user+"','legacy-storage-key','legacy.pdf','application/pdf',8,REPEAT('a',64),'PRIVATE','CLEAN',UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES (UUID(),'"+file+"','STUDENT_PROFILE','CV','"+user+"',UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO files(id,owner_user_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at) VALUES (UUID(),'"+user+"','legacy-pending-key','pending.pdf','application/pdf',8,REPEAT('b',64),'PRIVATE','PENDING',UTC_TIMESTAMP(6)),(UUID(),'"+user+"','legacy-rejected-key','rejected.pdf','application/pdf',8,REPEAT('c',64),'PRIVATE','REJECTED',UTC_TIMESTAMP(6))");
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertEquals(16,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals("44",flyway.info().current().getVersion().getVersion());assertEquals(0,flyway.migrate().migrationsExecuted);
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                try(ResultSet r=s.executeQuery("SELECT f.owner_user_id,f.storage_key,f.scan_status,b.resource_id,b.purpose FROM files f JOIN file_bindings b ON b.file_id=f.id WHERE f.id='"+file+"'")) {
                    assertTrue(r.next());assertEquals(user,r.getString(1));assertEquals("legacy-storage-key",r.getString(2));assertEquals("CLEAN",r.getString(3));assertEquals(user,r.getString(4));assertEquals("CV",r.getString(5));assertFalse(r.next());
                }
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM role_permissions WHERE permission_code IN ('file:upload','file:download')")) {assertTrue(r.next());assertEquals(8,r.getInt(1));}
                try(ResultSet r=s.executeQuery("SELECT scan_status,scan_attempt,scanner_reason_code,scan_lease_id,version FROM files WHERE storage_key IN ('legacy-pending-key','legacy-rejected-key') ORDER BY storage_key")) {
                    assertTrue(r.next());assertEquals("PENDING",r.getString(1));assertEquals(0,r.getInt(2));assertNull(r.getString(3));assertNull(r.getString(4));assertEquals(1,r.getLong(5));
                    assertTrue(r.next());assertEquals("REJECTED",r.getString(1));assertEquals(0,r.getInt(2));assertNull(r.getString(3));assertNull(r.getString(4));assertEquals(1,r.getLong(5));assertFalse(r.next());
                }
            }
        }
    }
    @Test void demandReferencesUseCanonicalSchoolTermAndSharedLedger() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString())) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load().migrate();
            String user=UUID.randomUUID().toString(),org=UUID.randomUUID().toString(),school=UUID.randomUUID().toString();
            String demand=UUID.randomUUID().toString(),term=UUID.randomUUID().toString(),key=UUID.randomUUID().toString();
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users VALUES ('"+user+"','rename@example.test','unusable-test-hash','Rename Test','ACTIVE',0,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO organizations(id,name,type,status,created_at) VALUES ('"+org+"','Test enterprise','ENTERPRISE','VERIFIED',UTC_TIMESTAMP(6)),('"+school+"','Test school','UNIVERSITY','VERIFIED',UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO terms(id,school_org_id,term_code,start_date,end_date,status,version,created_at,updated_at) VALUES ('"+term+"','"+school+"','TERM-1','2027-01-01','2027-06-01','ACTIVE',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at) VALUES ('"+demand+"','"+org+"','"+school+"','"+term+"','DRAFT','VALID',1,1,REPEAT('a',64),'{}',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO demand_revisions VALUES ('"+demand+"',1,1,REPEAT('a',64),'{}','{\"quantity\":3}','"+user+"','CREATE',UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO operation_records(id,actor_id,actor_scope_key,operation_code,idempotency_key,payload_hash,state,effect_ref,response_status,response_json,response_expires_at,created_at) VALUES (UUID(),'"+user+"','USER:"+user+"|ORG:"+org+"','DEMAND_CREATE','"+key+"',REPEAT('b',64),'DONE','"+demand+"',201,'{\"version\":1}',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY),UTC_TIMESTAMP(6))");
                assertThrows(SQLException.class,()->s.executeUpdate("UPDATE demands SET school_org_id='"+org+"' WHERE id='"+demand+"'"));
            }
            Flyway flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertEquals(0,flyway.migrate().migrationsExecuted);
            flyway.validate(); assertEquals(0,flyway.migrate().migrationsExecuted);
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                try(ResultSet r=s.executeQuery("SELECT d.id,JSON_EXTRACT(r.snapshot_json,'$.quantity'),JSON_EXTRACT(c.response_json,'$.version') FROM demands d JOIN demand_revisions r ON r.demand_id=d.id JOIN operation_records c ON c.effect_ref=d.id")) {
                    assertTrue(r.next()); assertEquals(demand,r.getString(1)); assertEquals(3,r.getInt(2)); assertEquals(1,r.getInt(3)); assertFalse(r.next());
                }
                assertThrows(SQLException.class,()->s.executeUpdate("DELETE FROM demands WHERE id='"+demand+"'"));
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('t5_demands','t5_demand_revisions','t5_demand_commands','demand_commands')")) {r.next();assertEquals(0,r.getInt(1));}
            }
        }
    }
    @Test void upgradesV2DataWithoutChangingIdentityAndEnforcesCoreConstraints() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString())) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("2").load().migrate();
            String user=UUID.randomUUID().toString(),member=UUID.randomUUID().toString();
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users VALUES ('"+user+"','upgrade@example.test','unusable-test-hash','Upgrade Test','ACTIVE',0,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO memberships VALUES ('"+member+"','"+user+"',NULL,'ADMIN',1,UTC_TIMESTAMP(6))");
            }
            Flyway flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            // V3-V7, shared ledger, T5 terms, T2 relationship and profile schemas.
            assertEquals(41,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals("44",flyway.info().current().getVersion().getVersion());assertEquals(0,flyway.migrate().migrationsExecuted);
            try(Connection c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());Statement s=c.createStatement()) {
                try(ResultSet r=s.executeQuery("SELECT email FROM users WHERE id='"+user+"'")) {assertTrue(r.next());assertEquals("upgrade@example.test",r.getString(1));}
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO memberships (id,user_id,role,active,created_at) VALUES ('"+UUID.randomUUID()+"','"+user+"','ADMIN',1,UTC_TIMESTAMP(6))"));
                assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO memberships (id,user_id,role,active,created_at) VALUES ('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','ADMIN',1,UTC_TIMESTAMP(6))"));
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('roles','user_roles')")) {r.next();assertEquals(0,r.getInt(1));}
                try(ResultSet r=s.executeQuery("SELECT COUNT(*) FROM core_locks WHERE lock_name='GLOBAL_ADMIN'")) {r.next();assertEquals(1,r.getInt(1));}
            }
        }
    }

    @Test void exactDecimalUpgradePreservesOldValuesAndAcceptsContractPrecision() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4").withPassword(UUID.randomUUID().toString())) {
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("33").load().migrate();
            String user=UUID.randomUUID().toString(),profile=UUID.randomUUID().toString();
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users VALUES ('"+user+"','exact-upgrade@example.test','unusable','Exact Upgrade','ACTIVE',0,0,NULL,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO student_profiles(id,user_id,skills_declared,profile_version,created_at,updated_at) VALUES ('"+profile+"','"+user+"',true,1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO student_profile_skills(profile_id,skill_code,ordinal,level,years_practice,source) VALUES('"+profile+"','JAVA',0,3,1.50,'SELF_DECLARED')");
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertEquals(11,flyway.migrate().migrationsExecuted);flyway.validate();assertEquals("44",flyway.info().current().getVersion().getVersion());
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword());var s=c.createStatement()) {
                try(var rows=s.executeQuery("SELECT years_practice FROM student_profile_skills WHERE profile_id='"+profile+"'")) {assertTrue(rows.next());assertEquals(0,new java.math.BigDecimal("1.50").compareTo(rows.getBigDecimal(1)));}
                String exact="1000.12345678901234567890123456789";
                s.executeUpdate("UPDATE student_profile_skills SET years_practice='"+exact+"' WHERE profile_id='"+profile+"'");
                try(var rows=s.executeQuery("SELECT years_practice FROM student_profile_skills WHERE profile_id='"+profile+"'")) {assertTrue(rows.next());assertEquals(exact,rows.getString(1));}
                assertThrows(SQLException.class,()->s.executeUpdate("UPDATE student_profile_skills SET years_practice='-0.001' WHERE profile_id='"+profile+"'"));
            }
            assertEquals(0,flyway.migrate().migrationsExecuted);
        }
    }

}
