package vn.utbridora.auth.modules.matching.advisory;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.matching.application.MatchingService;
import vn.utbridora.auth.operations.OperationErrors;
import static vn.utbridora.auth.modules.matching.advisory.AdvisoryModels.*;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;
import static org.junit.jupiter.api.Assertions.*;

/** Serializes actual production DTO/policy outputs for the independent JSON Schema checker. */
class AdvisorySchemaExportTest {
    @Test void serializeRealDtoShapesForIndependentSchemaValidation() throws Exception {
        String id="a17e2026-1121-4000-8000-000000000001";var now=Instant.parse("2026-11-21T00:00:00Z");
        var run=new MatchingService.MatchRunRef(id,id,2,1,"a".repeat(64),id,"b".repeat(64),"strict-v1","tax-v1","preferred-v1","COMPLETED",now,now,null);
        var rows=AdvisoryPolicy.rank(List.of(AdvisoryPolicyTest.row(1,90,.6),AdvisoryPolicyTest.row(2,70,.8),AdvisoryPolicyTest.row(3,100,null)));
        var values=List.of(Map.of("schema","AdvisoryAccepted","value",new Accepted(id,id,now)),
            Map.of("schema","AdvisoryMetadata","value",new Metadata(id,id,id,id,id,"READY",now,now.plusSeconds(1),now.plusSeconds(3600),versions(run),new Counts(3,2,1,0),null)),
            Map.of("schema","AdvisoryPage","value",new Page(id,id,"READY",AdvisoryPolicy.VERSION,now.plusSeconds(3600),3,2,2.0/3,0,25,3,1,rows)),
            Map.of("schema","Error","value",new OperationErrors.Error("AI_ADVISORY_CAPACITY_EXCEEDED","AI_ADVISORY_CAPACITY_EXCEEDED","Hàng đợi đang đầy.",id,List.of(),30)));
        Path output=Path.of("target","advisory-wire-values.json");Files.createDirectories(output.getParent());Files.writeString(output,JSON.writeValueAsString(values));assertEquals(4,JSON.readTree(Files.readString(output)).size());
    }
}
