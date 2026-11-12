package vn.utbridora.auth.modules.cooperation.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

/** Structural/domain checks only; authoritative catalog/school/term checks belong to the reference port. */
public final class DemandValidator {
    public static final long MAX_VERSION=9_007_199_254_740_991L;
    private DemandValidator() {}
    public static String uuid(String value, String field) {
        if(value==null || !value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new DemandFailure(400,"SCHEMA_VALIDATION","Mã định danh không hợp lệ.",
                    List.of(new FieldError(field,"INVALID_UUID","Cần mã UUID hợp lệ.")));
        return UUID.fromString(value).toString();
    }
    public static long version(Long value) {
        if(value==null || value<0 || value>MAX_VERSION) throw new DemandFailure(400,"SCHEMA_VALIDATION","Phiên bản không hợp lệ.");
        return value;
    }
    public static DemandInput validate(DemandInput input) {
        if(input==null) throw DemandFailure.invalid("data","Thiếu dữ liệu nhu cầu.");
        String school=uuid(input.schoolOrgId(),"schoolOrgId"), term=uuid(input.termId(),"termId");
        if(input.quantity()==null || input.quantity()<1 || input.quantity()>5000)
            throw DemandFailure.invalid("quantity","Chỉ tiêu phải từ 1 đến 5000.");
        if(input.deadline()==null) throw DemandFailure.invalid("deadline","Thiếu hạn tuyển.");
        RequiredCriteria r=input.required();
        if(r==null || r.dateRange()==null || r.dateRange().startDate()==null || r.dateRange().endDate()==null)
            throw DemandFailure.invalid("required.dateRange","Thiếu khoảng thời gian thực tập.");
        if(r.dateRange().endDate().isBefore(r.dateRange().startDate()))
            throw DemandFailure.invalid("required.dateRange","Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        if(r.majorCodes()==null) throw DemandFailure.invalid("required.majorCodes","Cần danh sách mã ngành, có thể rỗng.");
        List<String> majors=new ArrayList<>();
        for(String code:r.majorCodes()) {
            String normalized=text(code,1,64,"required.majorCodes",false);
            if(majors.contains(normalized)) throw DemandFailure.invalid("required.majorCodes","Mã ngành không được trùng.");
            majors.add(normalized);
        }
        List<SkillCriterion> required=skills(r.skills(),"required.skills");
        List<SkillCriterion> preferred=skills(input.preferred(),"preferred");
        // liên quan đến việc thêm lọc svien theo gpa là MVP: Chỉ nhận ngưỡng 1.0–4.0 theo bước 0.1.
        var minGpa=r.minGpa();
        if(minGpa!=null && (minGpa.compareTo(new java.math.BigDecimal("1.0"))<0 || minGpa.compareTo(new java.math.BigDecimal("4.0"))>0
                || minGpa.remainder(new java.math.BigDecimal("0.1")).signum()!=0))
            throw DemandFailure.invalid("required.minGpa","Ngưỡng GPA phải từ 1.0 đến 4.0 theo bước 0.1.");
        var codes=new HashSet<String>();
        required.forEach(s -> codes.add(s.skillCode()));
        for(SkillCriterion s:preferred) if(!codes.add(s.skillCode()))
            throw DemandFailure.invalid("preferred","Kỹ năng không được đồng thời bắt buộc và ưu tiên.");
        return new DemandInput(school,term,text(input.title(),1,200,"title",false),input.quantity(),
                text(input.description(),1,10000,"description",false),text(input.location(),1,500,"location",true),
                text(input.benefits(),0,2000,"benefits",true),input.deadline(),
                new RequiredCriteria(List.copyOf(majors),required,r.dateRange(),minGpa==null?null:minGpa.setScale(1)),preferred,
                text(input.academicCatalogVersion(),1,64,"academicCatalogVersion",false),
                text(input.taxonomyVersion(),1,64,"taxonomyVersion",false));
    }
    private static List<SkillCriterion> skills(List<SkillCriterion> skills,String field) {
        if(skills==null) throw DemandFailure.invalid(field,"Cần danh sách kỹ năng, có thể rỗng.");
        var result=new ArrayList<SkillCriterion>(); var codes=new HashSet<String>();
        for(SkillCriterion s:skills) {
            if(s==null) throw DemandFailure.invalid(field,"Kỹ năng không được null.");
            String code=text(s.skillCode(),1,64,field+".skillCode",false);
            if(!codes.add(code)) throw DemandFailure.invalid(field,"Mã kỹ năng không được trùng.");
            if(s.minLevel()!=null && (s.minLevel()<1 || s.minLevel()>5)) throw DemandFailure.invalid(field+".minLevel","Mức kỹ năng phải từ 1 đến 5.");
            if(s.minYearsPractice()!=null && s.minYearsPractice().signum()<0) throw DemandFailure.invalid(field+".minYearsPractice","Số năm kinh nghiệm không được âm.");
            result.add(new SkillCriterion(code,s.minLevel(),s.minYearsPractice()==null?null:s.minYearsPractice().stripTrailingZeros()));
        }
        return List.copyOf(result);
    }
    private static String text(String value,int min,int max,String field,boolean nullable) {
        if(value==null) { if(nullable) return null; throw DemandFailure.invalid(field,"Thiếu trường bắt buộc."); }
        String normalized=value.strip(); int length=normalized.codePointCount(0,normalized.length());
        if(length<min || length>max) throw DemandFailure.invalid(field,"Độ dài dữ liệu không hợp lệ.");
        return normalized;
    }
}
