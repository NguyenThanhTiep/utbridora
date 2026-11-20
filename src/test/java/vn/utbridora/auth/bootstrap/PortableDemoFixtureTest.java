package vn.utbridora.auth.bootstrap;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

class PortableDemoFixtureTest {
    @Test void distributableSnapshotContainsOnlySyntheticOwnersAndNoReusableCredentials()throws Exception {
        var manifest=JSON.readTree(new ClassPathResource("demo/portable/manifest.json").getContentAsByteArray());
        byte[] bytes=new ClassPathResource("demo/portable/full-cycle-v1.json.gz").getContentAsByteArray();
        assertEquals(manifest.path("sha256").asText(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        try(var stream=new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            var fixture=JSON.readTree(stream);var definitions=new HashMap<String,List<String>>();var counts=new HashMap<String,Integer>();
            for(var table:fixture.path("tables")){
                var cols=new ArrayList<String>();for(var col:table.path("columns"))cols.add(col.path("name").asText());
                definitions.put(table.path("name").asText(),cols);
            }
            int accounts=0;
            for(var batch:fixture.path("batches")) {
                String name=batch.path("table").asText();var cols=definitions.get(name);
                counts.merge(name,batch.path("rows").size(),Integer::sum);
                for(var row:batch.path("rows")) {
                    assertEquals(cols.size(),row.size());
                    if(name.equals("users")){
                        accounts++;assertTrue(row.get(cols.indexOf("id")).asText().startsWith("d3142026-1114-4000-8000-"));
                        assertTrue(row.get(cols.indexOf("email")).asText().endsWith("@demo.utbridora.test"));
                        assertEquals("${LOCAL_DEMO_PASSWORD_HASH}",row.get(cols.indexOf("password_hash")).asText());
                        assertTrue(row.get(cols.indexOf("locked_until")).isNull());
                    }
                    if(name.equals("mentor_invitation_refs"))assertTrue(row.get(cols.indexOf("token_id")).isNull());
                }
            }
            assertEquals(406,accounts);assertEquals(9,counts.get("organizations"));
            for(var table:fixture.path("tables"))assertEquals(table.path("rowCount").asInt(),counts.get(table.path("name").asText()));
            for(var forbidden:List.of("one_time_tokens","invitation_claims","SPRING_SESSION","SPRING_SESSION_ATTRIBUTES","activation_mail_outbox","operation_records","files","audit_logs","flyway_schema_history"))assertFalse(definitions.containsKey(forbidden));
        }
    }
}
