package vn.utbridora.auth.bootstrap;

import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.sql.DataSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import vn.utbridora.auth.core.PasswordPolicy;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

/** Local-only, atomic snapshot import after Flyway. A completed fixture is never replayed. */
@Configuration(proxyBeanMethods=false)
@Profile("local")
@ConditionalOnProperty(name="app.demo.portable.enabled",havingValue="true")
public class PortableDemoBootstrap {
    static final String ID="UTBRIDORA_FULL_CYCLE_20261120_V1";
    static final String ROOT="demo/portable/";
    private record Column(String name,String type) {}
    private record Table(String name,List<Column> columns,List<String> primaryKey,int rowCount) {}

    @Bean @Order(50)
    CommandLineRunner seedPortableDemo(JdbcTemplate jdbc,TransactionTemplate tx,DataSource dataSource,
            PasswordEncoder encoder,@Value("${DEMO_PASSWORD:}") String password) {
        return args->{
            if(!"utbridora".equals(jdbc.queryForObject("SELECT DATABASE()",String.class)))
                throw new IllegalStateException("Portable demo requires the local utbridora database");
            var manifest=JSON.readTree(new ClassPathResource(ROOT+"manifest.json").getContentAsByteArray());
            byte[] packed=new ClassPathResource(ROOT+"full-cycle-v1.json.gz").getContentAsByteArray();
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed));
            if(!ID.equals(manifest.path("fixtureId").asText()) || !hash.equals(manifest.path("sha256").asText()))
                throw new IllegalStateException("Portable demo checksum mismatch");
            JsonNode fixture;
            try(var input=new GZIPInputStream(new ByteArrayInputStream(packed))){fixture=JSON.readTree(input);}
            if(fixture.path("format").asInt()!=1 || !fixture.path("synthetic").asBoolean() || !ID.equals(fixture.path("fixtureId").asText()))
                throw new IllegalStateException("Unsupported portable demo fixture");
            var tables=definitions(fixture);
            jdbc.execute("CREATE TABLE IF NOT EXISTS portable_demo_receipts (fixture_id VARCHAR(64) PRIMARY KEY,sha256 CHAR(64) NOT NULL,state VARCHAR(16) NOT NULL,created_at DATETIME(6) NOT NULL)");
            tx.executeWithoutResult(status->{
                jdbc.queryForObject("SELECT lock_name FROM core_locks WHERE lock_name='GLOBAL_ADMIN' FOR UPDATE",String.class);
                var receipts=jdbc.queryForList("SELECT sha256,state FROM portable_demo_receipts WHERE fixture_id=?",ID);
                if(!receipts.isEmpty()) {
                    if(!hash.equals(receipts.getFirst().get("sha256")) || !"DONE".equals(receipts.getFirst().get("state")))
                        throw new IllegalStateException("Portable demo receipt differs; use the explicit reset-demo command");
                    return;
                }
                if(jdbc.queryForObject("SELECT COUNT(*) FROM users",Long.class)!=0 || jdbc.queryForObject("SELECT COUNT(*) FROM organizations",Long.class)!=0) {
                    LoggerFactory.getLogger(getClass()).warn("Existing database kept. Run run-local.cmd reset-demo to back up and replace local developer data.");
                    return;
                }
                PasswordPolicy.requireValid(password);
                String passwordHash=encoder.encode(password);
                Connection c=DataSourceUtils.getConnection(dataSource);
                try {
                    for(var batch:fixture.path("batches")) {
                        var table=Objects.requireNonNull(tables.get(batch.path("table").asText()));
                        String sql="INSERT INTO "+q(table.name())+" ("+String.join(",",table.columns().stream().map(col->q(col.name())).toList())+") VALUES ("+String.join(",",Collections.nCopies(table.columns().size(),"?"))+")";
                        // Flyway owns this single initial state row; the snapshot supplies its version.
                        if(table.name().equals("taxonomy_catalog_state")) sql+=" ON DUPLICATE KEY UPDATE taxonomy_version=VALUES(taxonomy_version),changed_at=VALUES(changed_at)";
                        try(var statement=c.prepareStatement(sql)) {
                            for(var row:batch.path("rows")) {
                                if(row.size()!=table.columns().size()) throw new IllegalStateException("Invalid demo row width");
                                for(int i=0;i<table.columns().size();i++) bind(statement,i+1,table.columns().get(i),row.get(i),passwordHash);
                                statement.addBatch();
                            }
                            statement.executeBatch();
                        }
                    }
                    // Only reviewed nullable back-references are deferred. FK/CHECK enforcement remains on.
                    for(var update:fixture.path("deferredUpdates")) {
                        var table=Objects.requireNonNull(tables.get(update.path("table").asText()));
                        var values=update.path("values");var keys=update.path("key");
                        var columns=table.columns().stream().filter(col->values.has(col.name())).toList();
                        var pk=table.columns().stream().filter(col->table.primaryKey().contains(col.name())).toList();
                        for(var col:columns) if(!allowedBackReference(table.name(),col.name()))throw new IllegalStateException("Unreviewed deferred reference");
                        String sql="UPDATE "+q(table.name())+" SET "+String.join(",",columns.stream().map(col->q(col.name())+"=?").toList())+" WHERE "+String.join(" AND ",pk.stream().map(col->q(col.name())+"=?").toList());
                        try(var statement=c.prepareStatement(sql)) {
                            int pos=1;for(var col:columns)bind(statement,pos++,col,values.get(col.name()),passwordHash);
                            for(var col:pk)bind(statement,pos++,col,keys.get(col.name()),passwordHash);
                            if(statement.executeUpdate()!=1)throw new IllegalStateException("Demo reference restoration failed");
                        }
                    }
                    for(var table:tables.values())
                        if(jdbc.queryForObject("SELECT COUNT(*) FROM "+q(table.name()),Long.class)!=table.rowCount())
                            throw new IllegalStateException("Portable demo row count differs: "+table.name());
                    jdbc.update("INSERT INTO portable_demo_receipts VALUES (?,?,'DONE',UTC_TIMESTAMP(6))",ID,hash);
                    LoggerFactory.getLogger(getClass()).info("Portable synthetic demo ready: 406 accounts, 3 schools, 6 enterprises, complete internship cycles");
                } catch(SQLException failure) {throw new IllegalStateException("Portable demo import rolled back",failure);}
                finally {DataSourceUtils.releaseConnection(c,dataSource);}
            });
        };
    }

    private static Map<String,Table> definitions(JsonNode fixture) {
        var tables=new LinkedHashMap<String,Table>();
        for(var table:fixture.path("tables")) {
            String name=table.path("name").asText();q(name);
            var columns=new ArrayList<Column>();for(var col:table.path("columns")){q(col.path("name").asText());columns.add(new Column(col.path("name").asText(),col.path("type").asText()));}
            var pk=new ArrayList<String>();for(var col:table.path("primaryKey"))pk.add(col.asText());
            if(pk.isEmpty() || tables.put(name,new Table(name,columns,pk,table.path("rowCount").asInt()))!=null)throw new IllegalStateException("Invalid demo table definition");
        }
        return tables;
    }
    private static boolean allowedBackReference(String table,String column) {
        return table.equals("demands") && column.equals("cooperation_id")
            || table.equals("internship_task_assignments") && column.equals("current_submission_id")
            || table.equals("internship_evaluations") && Set.of("current_version","published_version").contains(column);
    }
    private static String q(String identifier) {
        if(!identifier.matches("[A-Za-z][A-Za-z0-9_]*"))throw new IllegalStateException("Invalid demo identifier");
        return "`"+identifier+"`";
    }
    private static void bind(PreparedStatement statement,int position,Column column,JsonNode node,String passwordHash)throws SQLException {
        if(node==null || node.isNull()){statement.setObject(position,null);return;}
        String value=node.asText();
        if(column.name().equals("password_hash")) {
            if(!value.equals("${LOCAL_DEMO_PASSWORD_HASH}"))throw new IllegalStateException("Fixture contains a credential hash");
            statement.setString(position,passwordHash);return;
        }
        switch(column.type()) {
            case "bit","boolean","tinyint","smallint","mediumint","int","bigint","decimal","numeric","float","double" -> statement.setBigDecimal(position,new BigDecimal(value));
            case "binary","varbinary","blob","longblob","mediumblob","tinyblob" -> statement.setBytes(position,Base64.getDecoder().decode(value));
            default -> statement.setString(position,value);
        }
    }
}
