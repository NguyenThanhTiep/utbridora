package vn.utbridora.auth.operations;

import java.time.Instant;
import java.util.*;
import org.springframework.security.core.Authentication;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;
import vn.utbridora.auth.security.AppPrincipal;

/** Shared values only: these checks never grant authority. */
public final class WorkflowValues {
    private WorkflowValues() {}
    public static final JsonMapper JSON=JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public static String actor(Authentication auth) {
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal p)) throw failure(401,"SESSION_EXPIRED");
        return p.userId();
    }
    public static String uuid(String value) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw failure(400,"SCHEMA_VALIDATION");
        return value.toLowerCase(Locale.ROOT);
    }
    public static long version(Long value) {
        if(value==null || value<1 || value>9_007_199_254_740_991L) throw failure(400,"SCHEMA_VALIDATION");
        if(value==9_007_199_254_740_991L) throw failure(409,"VERSION_CONFLICT");
        return value;
    }
    public static String text(String value,int max) {
        if(value==null || value.isBlank() || value.strip().codePointCount(0,value.strip().length())>max) throw failure(400,"SCHEMA_VALIDATION");
        return value.trim();
    }
    public static String reason(String value) {
        if(value==null || !value.matches("[A-Z0-9_]{3,64}")) throw failure(400,"SCHEMA_VALIDATION");
        return value;
    }
    public static void period(Instant from,Instant until) {
        if(from==null || until==null || !from.isBefore(until)) throw failure(422,"INVALID_PERIOD");
    }
    public static void page(int page,int size) {
        if(page<0 || page>10000 || size<1 || size>100) throw failure(400,"INVALID_PAGINATION");
    }
    /** DATETIME columns store UTC wall time; do not let the Windows JVM timezone shift grants. */
    public static java.time.LocalDateTime utc(Instant value) {return java.time.LocalDateTime.ofInstant(value,java.time.ZoneOffset.UTC);}
    public static Instant instant(java.sql.ResultSet row,String column) throws java.sql.SQLException {
        var value=row.getObject(column,java.time.LocalDateTime.class);return value==null?null:value.toInstant(java.time.ZoneOffset.UTC);
    }
    public static <T> T body(String body,Class<T> type) {
        try {
            if(body==null || body.length()>2_000_000) throw failure(400,"SCHEMA_VALIDATION");
            var tree=JSON.readTree(body);checkObject(tree,type);
            return JSON.readValue(body,type);
        } catch(tools.jackson.core.JacksonException | IllegalArgumentException invalid) {throw failure(400,"SCHEMA_VALIDATION");}
    }
    private static void checkObject(tools.jackson.databind.JsonNode tree,Class<?> type) {
        if(tree==null || !tree.isObject()) throw failure(400,"SCHEMA_VALIDATION");
        Set<String> nullable=Set.of("reasonCode","reasonText","expiresAt","expectedBatchVersion","groupId","caseId");
        for(var field:type.getRecordComponents()) {
            var value=tree.get(field.getName());
            if(value==null || value.isNull() && !nullable.contains(field.getName())) throw failure(400,"SCHEMA_VALIDATION");
            if(value.isNull()) continue;
            if(field.getType().isRecord()) checkObject(value,field.getType());
            if(java.util.List.class.isAssignableFrom(field.getType())) {
                if(!value.isArray()) throw failure(400,"SCHEMA_VALIDATION");
                for(var item:value) if(item.isNull()) throw failure(400,"SCHEMA_VALIDATION");
            }
        }
    }
    public static OperationFailure failure(int status,String code) {
        return new OperationFailure(status,code,"Không thể thực hiện thao tác ("+code+").",null);
    }
}
