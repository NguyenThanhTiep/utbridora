package vn.utbridora.auth.onboarding;

import java.util.*;
import vn.utbridora.auth.files.TabularFileReader;
import vn.utbridora.auth.files.TabularFileReader.*;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Pure structural and row validation. It never creates an identity or queries foreign account PII. */
public final class ImportRowValidator {
    private static final Set<String> REQUIRED=Set.of("studentCode","email","fullName","majorCode","termCode");
    private static final Set<String> COLUMNS=Set.of("studentCode","email","fullName","majorCode","termCode","cohortCode","classCode","schoolOrgId");
    public record Validated(int rowNo,ImportSourceData sourceData,String validationState,List<String> errorCodes) {}
    private ImportRowValidator() {}
    public static List<Validated> validate(Table table,String school,String term,Set<String> majors) {
        if(!table.headers().containsAll(REQUIRED)) {
            var fields=List.of("studentCode","email","fullName","majorCode","termCode").stream().filter(name->!table.headers().contains(name))
                .map(name->Map.of("field","columns."+name,"code","REQUIRED_COLUMN_MISSING","message","Thiếu cột bắt buộc "+name+".")).toList();
            throw new vn.utbridora.auth.operations.OperationFailure(422,"FILE_STRUCTURE_INVALID","Tệp thiếu cột bắt buộc.",null,fields);
        }
        if(!COLUMNS.containsAll(table.headers())) throw new vn.utbridora.auth.operations.OperationFailure(422,"FILE_STRUCTURE_INVALID","Tệp có cột không được phép.",null,List.of(Map.of("field",table.headers().contains("schoolApproved")?"columns.schoolApproved":"columns","code",table.headers().contains("schoolApproved")?"FORBIDDEN_COLUMN":"UNKNOWN_COLUMN","message","Chỉ dùng các cột được hỗ trợ trong mẫu nhập.")));
        var rows=new ArrayList<Validated>();var codes=new HashMap<String,Integer>();var emails=new HashMap<String,Integer>();
        for(var row:table.rows()) {
            var errors=new LinkedHashSet<String>();var cells=new HashMap<String,Cell>();
            for(int i=0;i<table.headers().size();i++) cells.put(table.headers().get(i),row.cells().get(i));
            if(cells.containsKey("schoolOrgId") && !school.equals(value(cells.get("schoolOrgId")))) throw failure(403,"ACCESS_DENIED");
            var student=cells.get("studentCode");String code=value(student);
            if(student.kind()==Kind.NUMBER) {errors.add("STUDENT_CODE_MUST_BE_TEXT");code=null;}
            else if(student.kind()==Kind.FORMULA || code!=null && (length(code)>32 || code.codePoints().anyMatch(Character::isISOControl))) {errors.add("STUDENT_CODE_INVALID");code=null;}
            else if(code==null) errors.add("STUDENT_CODE_REQUIRED");
            String email=value(cells.get("email"));
            if(cells.get("email").kind()==Kind.FORMULA) errors.add("EMAIL_INVALID");
            else if(email==null) errors.add("EMAIL_REQUIRED");
            else {
                email=email.toLowerCase(Locale.ROOT);
                if(cells.get("email").kind()!=Kind.TEXT || email.length()>254 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) {email=null;errors.add("EMAIL_INVALID");}
            }
            String fullName=value(cells.get("fullName"));
            if(cells.get("fullName").kind()==Kind.FORMULA) errors.add("FULL_NAME_INVALID");
            else if(fullName==null) errors.add("FULL_NAME_REQUIRED");
            else if(cells.get("fullName").kind()!=Kind.TEXT || length(fullName)<2 || length(fullName)>150 || fullName.codePoints().anyMatch(Character::isISOControl)) {fullName=null;errors.add("FULL_NAME_INVALID");}
            String major=value(cells.get("majorCode")),termCode=value(cells.get("termCode"));
            if(cells.get("majorCode").kind()!=Kind.TEXT || major==null || major.length()>64 || !majors.contains(major)) {major=null;errors.add("MAJOR_INVALID");}
            if(cells.get("termCode").kind()!=Kind.TEXT || !term.equals(termCode)) {termCode=null;errors.add("TERM_CODE_INVALID");}
            String cohort=optionalCode(cells.get("cohortCode"),errors),clazz=optionalCode(cells.get("classCode"),errors);
            var source=new ImportSourceData(code,email,fullName,major,termCode,cohort,clazz);
            if(code!=null) codes.merge(code,1,Integer::sum);if(email!=null) emails.merge(email,1,Integer::sum);
            rows.add(new Validated(row.rowNo(),source,errors.isEmpty()?"VALID":"INVALID",List.copyOf(errors)));
        }
        return rows.stream().map(r->{
            var errors=new LinkedHashSet<>(r.errorCodes());var data=r.sourceData();
            if(data.studentCode()!=null && codes.getOrDefault(data.studentCode(),0)>1 || data.email()!=null && emails.getOrDefault(data.email(),0)>1) errors.add("DUPLICATE_IN_FILE");
            return new Validated(r.rowNo(),data,errors.isEmpty()?"VALID":"INVALID",List.copyOf(errors));
        }).toList();
    }
    private static int length(String value) {return value.codePointCount(0,value.length());}
    private static String optionalCode(Cell cell,Set<String> errors) {
        String value=value(cell);if(value==null) return null;
        if(cell.kind()!=Kind.TEXT || length(value)>64 || value.codePoints().anyMatch(Character::isISOControl)) {errors.add("PERMANENT_VALIDATION");return null;}return value;
    }
    private static String value(Cell cell) {if(cell==null || cell.kind()==Kind.FORMULA || cell.value()==null || cell.value().isBlank()) return null;return cell.value().trim();}
}
