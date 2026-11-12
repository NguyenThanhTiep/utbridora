package vn.utbridora.auth.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import vn.utbridora.auth.files.FileContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

final class FileUploadPolicy {
    static final Set<String> DOCUMENTS=Set.of("application/pdf","image/png","image/jpeg");
    record Policy(long maximumBytes,Set<String> contentTypes) {}
    static Policy forResource(ResourceRef resource,FilePurpose purpose) {
        if(resource==null || resource.resourceType()==null || purpose==null) throw failure(400,"SCHEMA_VALIDATION");
        boolean supported=switch(resource.resourceType()) {
            case STUDENT_PROFILE -> purpose==FilePurpose.CV || purpose==FilePurpose.EVIDENCE;
            case TASK_ASSIGNMENT -> purpose==FilePurpose.SUBMISSION;
            case WEEKLY_REPORT -> purpose==FilePurpose.WEEKLY_REPORT;
            case EVALUATION -> purpose==FilePurpose.EVIDENCE;
            case ENTERPRISE_VERIFICATION -> purpose==FilePurpose.VERIFICATION;
            case IMPORT_JOB -> purpose==FilePurpose.ROSTER_IMPORT;
            case COHORT -> purpose==FilePurpose.PLACEMENT_IMPORT;
        };
        if(!supported) throw failure(422,"BUSINESS_VALIDATION");
        return switch(purpose) {
            case SUBMISSION -> new Policy(20L*1024*1024,Set.of("application/pdf","application/zip"));
            case ROSTER_IMPORT,PLACEMENT_IMPORT -> new Policy(5L*1024*1024,Set.of("text/csv","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            default -> new Policy(10L*1024*1024,DOCUMENTS);
        };
    }
    static String name(String supplied) {
        if(supplied==null || supplied.codePoints().anyMatch(c->Character.isISOControl(c) || c==0x2028 || c==0x2029)) throw failure(400,"SCHEMA_VALIDATION");
        String name=Normalizer.normalize(supplied.replace('\\','/'),Normalizer.Form.NFC);
        name=name.substring(name.lastIndexOf('/')+1).trim();
        if(name.isBlank() || name.equals(".") || name.equals("..") || name.codePointCount(0,name.length())>255
            || name.codePoints().anyMatch(c->Character.isISOControl(c) || c==0x2028 || c==0x2029)) throw failure(400,"SCHEMA_VALIDATION");
        return name;
    }
    static void content(FileStorage.Staged staged,Policy policy,String type,String name) {
        if(type==null || !policy.contentTypes().contains(type)) throw failure(422,"BUSINESS_VALIDATION");
        // ZIP/XLSX/CSV stay closed until the bounded archive/import validators of T1-07 are provided.
        if(!DOCUMENTS.contains(type)) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        try {
            byte[] bytes=staged.prefix(16);String lower=name.toLowerCase(Locale.ROOT);
            boolean valid=switch(type) {
                case "application/pdf" -> bytes.length>=8 && new String(bytes,0,5,StandardCharsets.US_ASCII).equals("%PDF-") && lower.endsWith(".pdf");
                case "image/png" -> bytes.length>=16 && Arrays.equals(Arrays.copyOf(bytes,8),new byte[]{(byte)137,80,78,71,13,10,26,10})
                    && bytes[8]==0 && bytes[9]==0 && bytes[10]==0 && bytes[11]==13
                    && new String(bytes,12,4,StandardCharsets.US_ASCII).equals("IHDR") && lower.endsWith(".png");
                case "image/jpeg" -> bytes.length>=4 && bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255
                    && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
                default -> false;
            };
            if(!valid) throw failure(422,"BUSINESS_VALIDATION");
        } catch(IOException error) {throw failure(503,"FILE_STORAGE_UNAVAILABLE");}
    }
    static String extension(String type) {return switch(type) {case "application/pdf"->"pdf";case "image/png"->"png";case "image/jpeg"->"jpg";default->"bin";};}
}
