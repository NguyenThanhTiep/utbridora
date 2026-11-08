package vn.utbridora.auth.modules.student.application;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/**
 * Reads ProfileInput as a closed object (Contract C.0 v1.0 line 84): every field must be present, unknown
 * fields are rejected and Text values are trimmed and measured in code points. Single use per request.
 */
final class ProfileInputParser {
    static final Pattern UUID_FORMAT=Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern DATE_FORMAT=Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final long MAX_VERSION=9_007_199_254_740_991L;
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final Set<String> INPUT_KEYS=Set.of("expectedProfileVersion","termId","majorCode","skillsDeclared","skills",
        "customSkills","availabilityDeclared","availablePeriods","orientationCodes","subjectCodes","projects","certificates","cvFileId","phone");
    private static final Set<String> CANONICAL_KEYS=Set.of("skillCode","level","yearsPractice","note");
    private static final Set<String> CUSTOM_KEYS=Set.of("customSkillId","name","categoryCode","level","yearsPractice","note");
    private static final Set<String> PERIOD_KEYS=Set.of("startDate","endDate");
    private static final Set<String> PROJECT_KEYS=Set.of("projectId","title","description","role","skillCodes","url","evidenceFileIds");
    private static final Set<String> CERTIFICATE_KEYS=Set.of("certificateId","title","issuer","issuedOn","expiresOn","fileId");
    private static final String EMPTY_WHEN_UNDECLARED="Phải là danh sách rỗng khi chưa khai báo.";

    private final List<FieldErrorView> errors=new ArrayList<>();

    static boolean isUuid(String value){ return value!=null && UUID_FORMAT.matcher(value).matches(); }

    ProfileInput parse(String body) {
        Object raw;
        try { raw=JSON.readValue(body==null?"":body,Object.class); }
        catch (JacksonException e) { raw=null; }
        Map<?,?> root=object("",raw,INPUT_KEYS);
        if(root==null) throw invalid();
        Long version=version(root,"expectedProfileVersion");
        String termId=uuid(root,"","termId",false);
        String majorCode=text(root,"","majorCode",1,64,true);
        Boolean skillsDeclared=bool(root,"","skillsDeclared");
        List<CanonicalSkillInput> skills=list(root,"","skills",this::canonical);
        Set<String> skillCodes=new HashSet<>(); // K.2 line 1845: UNIQUE(profile_id, skill_code)
        for(int i=0;i<skills.size();i++) if(!skillCodes.add(skills.get(i).skillCode())) error("skills["+i+"].skillCode","Giá trị bị trùng.");
        List<CustomSkillInput> customSkills=list(root,"","customSkills",this::custom);
        Boolean availabilityDeclared=bool(root,"","availabilityDeclared");
        List<DateRange> periods=list(root,"","availablePeriods",this::period);
        List<String> orientationCodes=codes(root,"","orientationCodes");
        List<String> subjectCodes=codes(root,"","subjectCodes");
        List<Project> projects=list(root,"","projects",this::project);
        List<Certificate> certificates=list(root,"","certificates",this::certificate);
        String cvFileId=uuid(root,"","cvFileId",true);
        String phone=text(root,"","phone",8,20,true);
        // BC11 v1.0 line 784: an undeclared section must be empty; declared with [] is an intentional empty answer.
        if(Boolean.FALSE.equals(skillsDeclared)) {
            if(!skills.isEmpty()) error("skills",EMPTY_WHEN_UNDECLARED);
            if(!customSkills.isEmpty()) error("customSkills",EMPTY_WHEN_UNDECLARED);
        }
        if(Boolean.FALSE.equals(availabilityDeclared) && !periods.isEmpty()) error("availablePeriods",EMPTY_WHEN_UNDECLARED);
        if(!errors.isEmpty()) throw invalid();
        return new ProfileInput(version,termId,majorCode,skillsDeclared,skills,customSkills,availabilityDeclared,periods,
            orientationCodes,subjectCodes,projects,certificates,cvFileId,phone);
    }

    private CanonicalSkillInput canonical(Object raw,String path) {
        Map<?,?> m=object(path,raw,CANONICAL_KEYS); if(m==null) return null;
        String code=text(m,path,"skillCode",1,64,false); Integer level=level(m,path); BigDecimal years=decimal(m,path,"yearsPractice");
        String note=text(m,path,"note",0,1000,true);
        return code==null || level==null || years==null ? null : new CanonicalSkillInput(code,level,years,note);
    }
    private CustomSkillInput custom(Object raw,String path) {
        Map<?,?> m=object(path,raw,CUSTOM_KEYS); if(m==null) return null;
        String id=uuid(m,path,"customSkillId",true), name=text(m,path,"name",1,128,false), category=text(m,path,"categoryCode",1,64,false);
        Integer level=level(m,path); BigDecimal years=decimal(m,path,"yearsPractice"); String note=text(m,path,"note",0,1000,true);
        return name==null || category==null || level==null || years==null ? null : new CustomSkillInput(id,name,category,level,years,note);
    }
    private DateRange period(Object raw,String path) {
        Map<?,?> m=object(path,raw,PERIOD_KEYS); if(m==null) return null;
        String start=date(m,path,"startDate",false), end=date(m,path,"endDate",false);
        if(start==null || end==null) return null;
        if(start.compareTo(end)>0) { error(path,"Ngày bắt đầu phải trước hoặc trùng ngày kết thúc."); return null; }
        return new DateRange(start,end);
    }
    private Project project(Object raw,String path) {
        Map<?,?> m=object(path,raw,PROJECT_KEYS); if(m==null) return null;
        String id=uuid(m,path,"projectId",true), title=text(m,path,"title",1,200,false);
        String description=text(m,path,"description",0,5000,false), role=text(m,path,"role",0,200,false);
        List<String> skillCodes=codes(m,path,"skillCodes"); String url=httpsUrl(m,path);
        List<String> evidence=uuids(m,path,"evidenceFileIds");
        return title==null || description==null || role==null ? null : new Project(id,title,description,role,skillCodes,url,evidence);
    }
    private Certificate certificate(Object raw,String path) {
        Map<?,?> m=object(path,raw,CERTIFICATE_KEYS); if(m==null) return null;
        String id=uuid(m,path,"certificateId",true), title=text(m,path,"title",1,200,false), issuer=text(m,path,"issuer",1,200,false);
        String issuedOn=date(m,path,"issuedOn",false), expiresOn=date(m,path,"expiresOn",true), fileId=uuid(m,path,"fileId",true);
        return title==null || issuer==null || issuedOn==null ? null : new Certificate(id,title,issuer,issuedOn,expiresOn,fileId);
    }

    private Map<?,?> object(String path,Object raw,Set<String> keys) {
        if(!(raw instanceof Map<?,?> map)) { error(path.isEmpty()?"body":path,"Phải là một đối tượng JSON."); return null; }
        for(String key:keys) if(!map.containsKey(key)) error(join(path,key),"Thiếu trường bắt buộc.");
        for(Object key:map.keySet()) if(!keys.contains(String.valueOf(key))) error(join(path,String.valueOf(key)),"Trường không được phép.");
        return map;
    }
    private <T> List<T> list(Map<?,?> m,String path,String name,BiFunction<Object,String,T> item) {
        if(!m.containsKey(name)) return List.of();
        Object value=m.get(name); String field=join(path,name);
        if(!(value instanceof List<?> raw)) { error(field,value==null?"Không được để null.":"Phải là danh sách."); return List.of(); }
        List<T> result=new ArrayList<>();
        for(int i=0;i<raw.size();i++) { T parsed=item.apply(raw.get(i),field+"["+i+"]"); if(parsed!=null) result.add(parsed); }
        return result;
    }
    private List<String> codes(Map<?,?> m,String path,String name) {
        return list(m,path,name,(raw,field)->text(Collections.singletonMap("value",raw),"value",1,64,false,field));
    }
    private List<String> uuids(Map<?,?> m,String path,String name) {
        List<String> values=list(m,path,name,(raw,field)->uuid(Collections.singletonMap("value",raw),"value",false,field));
        if(new HashSet<>(values).size()!=values.size()) error(join(path,name),"Giá trị bị trùng.");
        return values;
    }
    private String text(Map<?,?> m,String path,String name,int min,int max,boolean nullable) {
        return text(m,name,min,max,nullable,join(path,name));
    }
    private String text(Map<?,?> m,String name,int min,int max,boolean nullable,String field) {
        if(!m.containsKey(name)) return null;
        Object value=m.get(name);
        if(value==null) { if(!nullable) error(field,"Không được để null."); return null; }
        if(!(value instanceof String s)) { error(field,"Phải là chuỗi."); return null; }
        String trimmed=s.strip(); int length=trimmed.codePointCount(0,trimmed.length());
        if(length<min || length>max) { error(field,"Độ dài phải từ "+min+" đến "+max+" ký tự."); return null; }
        return trimmed;
    }
    private String uuid(Map<?,?> m,String path,String name,boolean nullable) { return uuid(m,name,nullable,join(path,name)); }
    private String uuid(Map<?,?> m,String name,boolean nullable,String field) {
        if(!m.containsKey(name)) return null;
        Object value=m.get(name);
        if(value==null) { if(!nullable) error(field,"Không được để null."); return null; }
        if(!(value instanceof String s) || !isUuid(s)) { error(field,"Phải là UUID."); return null; }
        return s.toLowerCase(Locale.ROOT);
    }
    private String date(Map<?,?> m,String path,String name,boolean nullable) {
        if(!m.containsKey(name)) return null;
        Object value=m.get(name); String field=join(path,name);
        if(value==null) { if(!nullable) error(field,"Không được để null."); return null; }
        if(value instanceof String s && DATE_FORMAT.matcher(s).matches()) {
            try { LocalDate.parse(s); return s; } catch (DateTimeParseException ignored) { /* reported below */ }
        }
        error(field,"Phải là ngày dạng yyyy-MM-dd."); return null;
    }
    private Boolean bool(Map<?,?> m,String path,String name) {
        if(!m.containsKey(name)) return null;
        if(m.get(name) instanceof Boolean value) return value;
        error(join(path,name),"Phải là true hoặc false."); return null;
    }
    private Integer level(Map<?,?> m,String path) {
        if(!m.containsKey("level")) return null;
        BigInteger value=integral(m.get("level"));
        if(value!=null && value.compareTo(BigInteger.ONE)>=0 && value.compareTo(BigInteger.valueOf(5))<=0) return value.intValue();
        error(join(path,"level"),"Mức độ phải là số nguyên từ 1 đến 5."); return null;
    }
    private BigDecimal decimal(Map<?,?> m,String path,String name) {
        if(!m.containsKey(name)) return null;
        // Stored as DECIMAL(5,2), the precision T2 chose for years of practice (K.2 line 1845).
        if(m.get(name) instanceof Number number) {
            try {
                BigDecimal value=new BigDecimal(number.toString());
                if(value.signum()>=0 && value.compareTo(new BigDecimal("999.99"))<=0 && value.stripTrailingZeros().scale()<=2) return value;
            } catch (NumberFormatException ignored) { /* Infinity/NaN from overflowing input; reported below */ }
        }
        error(join(path,name),"Phải là số từ 0 đến 999,99, tối đa 2 chữ số thập phân."); return null;
    }
    private Long version(Map<?,?> m,String name) {
        if(!m.containsKey(name)) return null;
        BigInteger value=integral(m.get(name));
        if(value!=null && value.signum()>=0 && value.compareTo(BigInteger.valueOf(MAX_VERSION))<=0) return value.longValue();
        error(name,"Phải là số nguyên không âm."); return null;
    }
    private String httpsUrl(Map<?,?> m,String path) {
        String value=text(m,path,"url",1,2048,true); // column limit; the contract sets no length for HTTPSUrl
        if(value==null) return null;
        try {
            URI uri=new URI(value);
            if("https".equalsIgnoreCase(uri.getScheme()) && uri.getHost()!=null && uri.getUserInfo()==null) return value;
        } catch (URISyntaxException ignored) { /* reported below */ }
        error(join(path,"url"),"Phải là URL https tuyệt đối, không chứa thông tin đăng nhập."); return null;
    }
    private static BigInteger integral(Object value) {
        if(value instanceof Integer || value instanceof Long || value instanceof Short) return BigInteger.valueOf(((Number)value).longValue());
        return value instanceof BigInteger big ? big : null;
    }
    private static String join(String path,String name){ return path.isEmpty()?name:path+"."+name; }
    private void error(String field,String message){ errors.add(new FieldErrorView(field,"SCHEMA_VALIDATION",message)); }
    private StudentProfileException invalid() {
        return new StudentProfileException(400,"SCHEMA_VALIDATION","Dữ liệu hồ sơ chưa đúng định dạng.",errors);
    }
}
