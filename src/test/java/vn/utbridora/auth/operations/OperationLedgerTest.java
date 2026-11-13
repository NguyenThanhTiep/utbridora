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

    @Test void decimalHashPreservesPrecision() {
        assertNotEquals(OperationLedger.payloadHash(Map.of("years",new java.math.BigDecimal("1.0000000000000000001"))),
            OperationLedger.payloadHash(Map.of("years",new java.math.BigDecimal("1.0000000000000000002"))));
    }
    @Test void exactLegacyNumericPayloadHashesStayCompatible() throws Exception {
        var legacy=tools.jackson.databind.json.JsonMapper.builder().build();
        for(String value:List.of("0","1","1.0","1.50","1.001","66.67","999.99","1000")) {
            var input=Map.of("years",new java.math.BigDecimal(value));
            Object old=legacy.convertValue(input,Object.class);
            byte[] bytes=legacy.writeValueAsString(old).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertEquals(hash,OperationLedger.payloadHash(input),"Changed legacy numeric hash for "+value);
        }
    }

}
