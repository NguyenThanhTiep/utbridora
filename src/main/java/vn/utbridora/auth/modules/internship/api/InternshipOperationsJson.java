package vn.utbridora.auth.modules.internship.api;

import java.util.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Closed CP-WS commands, including nullable-but-required note/comment. */
public final class InternshipOperationsJson {
    private static final JsonMapper MAPPER=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY,DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private InternshipOperationsJson() {}
    public static <T> T read(String body,Class<T> type) {
        if(body==null || body.length()>2_000_000) throw failure(400,"SCHEMA_VALIDATION");
        try {var node=MAPPER.readTree(body);check(node,type);return MAPPER.treeToValue(node,type);}
        catch(tools.jackson.core.JacksonException invalid) {
            for(Throwable cause=invalid;cause!=null;cause=cause.getCause()) if(cause instanceof vn.utbridora.auth.operations.OperationFailure domain) throw domain;
            throw failure(400,"SCHEMA_VALIDATION");
        } catch(IllegalArgumentException invalid) {throw failure(400,"SCHEMA_VALIDATION");}
    }
    private static void check(JsonNode node,Class<?> type) {
        if(node==null || !node.isObject()) throw failure(400,"SCHEMA_VALIDATION");
        Set<String> keys=new HashSet<>(node.propertyNames());Set<String> expected=new HashSet<>();
        for(var field:type.getRecordComponents()) {
            String key=field.getName();expected.add(key);var value=node.get(key);
            if(value==null || value.isNull() && !Set.of("note","comment").contains(key)) throw failure(400,"SCHEMA_VALIDATION");
            if(value.isNull()) continue;
            Class<?> fieldType=field.getType();
            if(fieldType==UUID.class) {if(!value.isString()) throw failure(400,"SCHEMA_VALIDATION");uuid(value.asString());}
            else if(fieldType==Long.class || fieldType==long.class || fieldType==int.class) {if(!value.isIntegralNumber() || !value.canConvertToLong()) throw failure(400,"SCHEMA_VALIDATION");}
            else if(fieldType==Boolean.class || fieldType==boolean.class) {if(!value.isBoolean()) throw failure(400,"SCHEMA_VALIDATION");}
            else if(fieldType==String.class || fieldType.isEnum()) {if(!value.isString()) throw failure(400,"SCHEMA_VALIDATION");}
            else if(List.class.isAssignableFrom(fieldType)) {
                if(!value.isArray()) throw failure(400,"SCHEMA_VALIDATION");
                var itemType=(Class<?>)((java.lang.reflect.ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
                for(var item:value) check(item,itemType);
            }
        }
        if(!keys.equals(expected)) throw failure(400,"SCHEMA_VALIDATION");
    }
}
