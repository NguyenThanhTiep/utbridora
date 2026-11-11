package vn.utbridora.auth.operations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OperationLedgerTest {
    @Test void canonicalizesNestedObjectKeysButPreservesOrdinaryArrayOrder() {
        var first=new LinkedHashMap<String,Object>();first.put("b",2);first.put("a",Map.of("y",2,"x",1));
        var second=new LinkedHashMap<String,Object>();second.put("a",Map.of("x",1,"y",2));second.put("b",2);
        assertEquals(OperationLedger.payloadHash(first),OperationLedger.payloadHash(second));
        assertNotEquals(OperationLedger.payloadHash(List.of("a","b")),OperationLedger.payloadHash(List.of("b","a")));
        assertEquals(OperationLedger.payloadHash(Map.of("caseIds",List.of("a","b"))),
                OperationLedger.payloadHash(Map.of("caseIds",List.of("b","a"))));
    }
}
