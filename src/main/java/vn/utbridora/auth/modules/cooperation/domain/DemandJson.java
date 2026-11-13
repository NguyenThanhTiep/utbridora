package vn.utbridora.auth.modules.cooperation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

public final class DemandJson {
    private static final JsonMapper JSON=JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    private DemandJson() {}
    public static String write(Object value) { return JSON.writeValueAsString(value); }
    public static <T> T read(String value,Class<T> type) { return JSON.readValue(value,type); }
    public static <T> T request(JsonNode node,Class<T> type) {
        try {
            JsonNode normalized=node;
            if(type==DemandInput.class && node.isObject() && node.get("required") instanceof ObjectNode required && !required.has("minGpa")) {
                normalized=node.deepCopy();
                ((ObjectNode)normalized.get("required")).putNull("minGpa");
            }
            checkNulls(normalized,type); return JSON.treeToValue(normalized,type);
        }
        catch(RuntimeException ex) { throw new DemandFailure(400,"SCHEMA_VALIDATION","Kiểm tra trường dữ liệu, kiểu dữ liệu và các trường bắt buộc."); }
    }
    private static void checkNulls(JsonNode node,Class<?> type) {
        if(!node.isObject()) throw new IllegalArgumentException();
        for(var field:type.getRecordComponents()) {
            JsonNode value=node.get(field.getName());
            if(value==null) throw new IllegalArgumentException();
            boolean nullable=java.util.Set.of("location","benefits","minLevel","minYearsPractice","minGpa").contains(field.getName());
            if(value.isNull()) { if(!nullable) throw new IllegalArgumentException(); continue; }
            if((field.getType()==String.class || field.getType()==java.time.Instant.class || field.getType()==java.time.LocalDate.class)
                    && !value.getNodeType().name().equals("STRING")) throw new IllegalArgumentException();
            if(field.getType().isRecord()) checkNulls(value,field.getType());
            else if(java.util.List.class.isAssignableFrom(field.getType())) {
                if(!value.isArray()) throw new IllegalArgumentException();
                Class<?> item=(Class<?>)((java.lang.reflect.ParameterizedType)field.getGenericType()).getActualTypeArguments()[0];
                for(JsonNode element:value) {
                    if(element.isNull() || item==String.class && !element.getNodeType().name().equals("STRING")) throw new IllegalArgumentException();
                    if(item.isRecord()) checkNulls(element,item);
                }
            }
        }
    }
    public static String requestHash(Object request) { return sha256(canonical(JSON.valueToTree(request))); }
    /** Provisional policy: descriptive fields/deadline do not change criteria; quantity and scope do. */
    public static String criteriaHash(DemandInput input) {
        var data=new TreeMap<String,Object>();
        data.put("schoolOrgId",input.schoolOrgId()); data.put("termId",input.termId());
        data.put("quantity",input.quantity()); data.put("academicCatalogVersion",input.academicCatalogVersion());
        data.put("taxonomyVersion",input.taxonomyVersion());
        var required=new TreeMap<String,Object>();
        required.put("majorCodes",input.required().majorCodes().stream().sorted().toList());
        required.put("skills",input.required().skills().stream().sorted(Comparator.comparing(SkillCriterion::skillCode)).toList());
        required.put("dateRange",input.required().dateRange());
        // liên quan đến việc thêm lọc svien theo gpa là MVP: Đưa ngưỡng GPA vào criteria hash để thay đổi ngưỡng tạo phiên bản matching mới.
        required.put("minGpa",input.required().minGpa());
        data.put("required",required);
        data.put("preferred",input.preferred().stream().sorted(Comparator.comparing(SkillCriterion::skillCode)).toList());
        return sha256(canonical(JSON.valueToTree(data)));
    }
    private static String canonical(JsonNode node) {
        if(node.isObject()) {
            var names=new ArrayList<String>(); node.propertyNames().forEach(names::add); names.sort(String::compareTo);
            return "{"+String.join(",",names.stream().map(name -> write(name)+":"+canonical(node.get(name))).toList())+"}";
        }
        if(node.isArray()) { var values=new ArrayList<String>(); node.forEach(n -> values.add(canonical(n))); return "["+String.join(",",values)+"]"; }
        if(node.isNumber()) return node.decimalValue().stripTrailingZeros().toPlainString();
        return node.toString();
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
