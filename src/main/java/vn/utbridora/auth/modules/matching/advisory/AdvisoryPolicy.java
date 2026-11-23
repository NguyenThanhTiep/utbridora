package vn.utbridora.auth.modules.matching.advisory;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort.*;
import vn.utbridora.auth.modules.cooperation.integration.DemandSemanticSnapshotPort.Snapshot;
import static vn.utbridora.auth.operations.WorkflowValues.JSON;

public final class AdvisoryPolicy {
    public static final String VERSION="semantic-desc-canonical-tie-v1";
    public record Item(String studentUserId,String profileSnapshotId,long profileVersion,String relevantHash,String overallStatus,BigDecimal fitScore,int position,Integer rank,BigDecimal semanticScore,String scoreStatus,Boolean limited,String reasonCode) {}
    public record Unranked(Entry entry,BigDecimal fitScore,Double cosine,Boolean limited,String reason) {}
    public static List<Item> rank(List<Unranked> inputs) {
        if(inputs.size()>5000 || inputs.stream().map(x->x.entry().studentUserId()).distinct().count()!=inputs.size()) throw new IllegalArgumentException("Invalid advisory population");
        var sorted=inputs.stream().sorted(Comparator.<Unranked,Integer>comparing(x->x.cosine()==null?1:0)
            .thenComparing(x->x.cosine()==null?BigDecimal.ZERO:score(x.cosine()),Comparator.reverseOrder())
            .thenComparing(Unranked::fitScore,Comparator.reverseOrder()).thenComparing(x->x.entry().studentUserId())).toList();
        var rows=new ArrayList<Item>();
        for(var x:sorted) {int pos=rows.size()+1;boolean scored=x.cosine()!=null;var e=x.entry();
            rows.add(new Item(e.studentUserId(),e.profileSnapshotId(),e.profileVersion(),e.relevantHash(),"MATCH",x.fitScore(),pos,scored?pos:null,scored?score(x.cosine()):null,scored?"SCORED":"INPUT_TOO_LARGE".equals(x.reason())?"INPUT_TOO_LARGE":"NO_CONTENT",scored?Boolean.TRUE.equals(x.limited()):null,scored?null:x.reason()));
        }return List.copyOf(rows);
    }
    public static BigDecimal score(double value) {if(!Double.isFinite(value) || value < -1 || value > 1) throw new IllegalArgumentException("Invalid cosine");return BigDecimal.valueOf(value).setScale(6,RoundingMode.HALF_UP);}
    public static String query(Snapshot s) {return "Internship demand\nTitle: "+JSON.writeValueAsString(s.title())+"\nDescription: "+JSON.writeValueAsString(s.description());}
    public static String passage(Entry e) {
        var s=new StringBuilder("Student-declared skills and projects");
        for(var v:e.skills()) {var m=new LinkedHashMap<String,Object>();m.put("kind",v.kind());m.put("codeOrName",v.codeOrName());m.put("categoryCode",v.categoryCode());m.put("level",v.level());m.put("yearsPractice",v.yearsPractice());m.put("note",v.note());m.put("source",v.source());s.append("\nSkill: ").append(JSON.writeValueAsString(m));}
        for(var v:e.projects()) {var m=new LinkedHashMap<String,Object>();m.put("title",v.title());m.put("description",v.description());m.put("role",v.role());m.put("skillCodes",v.skillCodes());m.put("source",v.source());s.append("\nProject: ").append(JSON.writeValueAsString(m));}
        return s.toString();
    }
    public static boolean bounded(String s) {
        if(s.codePointCount(0,s.length())>8192 || s.getBytes(StandardCharsets.UTF_8).length>32768) return false;
        for(int i=0;i<s.length();i++) if(Character.isSurrogate(s.charAt(i))) {if(!Character.isHighSurrogate(s.charAt(i)) || i+1>=s.length() || !Character.isLowSurrogate(s.charAt(++i))) return false;}
        return true;
    }
    public static String inputHash(String role,String text) {
        try {return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(List.of(vn.utbridora.auth.modules.matching.semantic.SemanticProtocol.ADVISORY_TEMPLATE,role,text))));}
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private AdvisoryPolicy() {}
}
