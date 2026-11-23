package vn.utbridora.auth.modules.matching.semantic;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;

/** Immutable internal data. Snapshot refs stay in Java; the wire uses ordinal keys only. */
record SemanticWork(Context context,String query,String queryHash,List<Item> items) {
    record Context(String runId,String schoolOrgId,String termId,String datasetSnapshotId,
                   String profileSnapshotVersion,long demandVersion,long criteriaVersion,String criteriaHash,
                   String taxonomyVersion,String academicCatalogVersion) {}
    record Ref(String studentUserId,String profileSnapshotId,long profileVersion,String relevantHash,String inputHash) {}
    record Item(String key,String text,Ref ref) {}
    record Score(Ref ref,Double score,String status,int queryChunks,int passageChunks,boolean limited) {}
    enum Status {PENDING,READY,NO_DATA,UNAVAILABLE,FAILED,REJECTED}
    record Outcome(Context context,String queryHash,Status status,List<Score> scores,String modelRevision,
                   String templateVersion,String aggregationVersion,String failureCode,Instant expiresAt) {}

    static SemanticWork build(String runId,DemandCriteriaSnapshot criteria,SnapshotSet dataset,List<ProfileSnapshot> profiles) {
        if (!Objects.equals(criteria.schoolOrgId(),dataset.schoolOrgId()) || !Objects.equals(criteria.termId(),dataset.termId())
                || !Objects.equals(criteria.taxonomyVersion(),dataset.taxonomyVersion())
                || !Objects.equals(criteria.academicCatalogVersion(),dataset.academicCatalogVersion())
                || profiles.size()!=dataset.profileCount()) throw new IllegalArgumentException("INVALID_SNAPSHOT");
        var context=new Context(runId,dataset.schoolOrgId(),dataset.termId(),dataset.datasetSnapshotId(),
            dataset.profileSnapshotVersion(),criteria.demandVersion(),criteria.criteriaVersion(),criteria.criteriaHash(),
            criteria.taxonomyVersion(),criteria.academicCatalogVersion());
        String query=queryText(criteria);
        var seen=new HashSet<String>();var items=new ArrayList<Item>();
        for (var profile:profiles) {
            if (!seen.add(profile.studentUserId()) || !Objects.equals(profile.schoolOrgId(),dataset.schoolOrgId())
                    || !Objects.equals(profile.termId(),dataset.termId())
                    || !Objects.equals(profile.taxonomyVersion(),dataset.taxonomyVersion())
                    || !Objects.equals(profile.academicCatalogVersion(),dataset.academicCatalogVersion()))
                throw new IllegalArgumentException("INVALID_SNAPSHOT");
            String text=profileText(profile);
            items.add(new Item(Integer.toString(items.size()),text,new Ref(profile.studentUserId(),
                profile.profileSnapshotId(),profile.profileVersion(),profile.relevantHash(),hash("passage\n"+text))));
        }
        return new SemanticWork(context,query,hash("query\n"+query),List.copyOf(items));
    }
    private static String queryText(DemandCriteriaSnapshot c) {
        var text=new StringBuilder("Structured internship requirements.\nMajors: ");
        text.append(c.required().majorCodes().isEmpty()?"unrestricted":String.join(", ",new TreeSet<>(c.required().majorCodes())));
        text.append("\nRequired canonical skills:");
        appendCriteria(text,c.required().skills());text.append("\nPreferred canonical skills:");appendCriteria(text,c.preferred());
        text.append("\nRequired availability: ").append(c.required().dateRange().startDate()).append(" to ").append(c.required().dateRange().endDate());
        return bounded(text.toString());
    }
    private static void appendCriteria(StringBuilder text,List<SkillCriterion> skills) {
        if (skills.isEmpty()) text.append(" none");
        for(var s:skills.stream().sorted(Comparator.comparing(SkillCriterion::skillCode)).toList())
            text.append("\n").append(s.skillCode()).append("; minimum level: ").append(s.minLevel()==null?"unspecified":s.minLevel())
                .append("; minimum practice years: ").append(s.minYearsPractice()==null?"unspecified":s.minYearsPractice().stripTrailingZeros().toPlainString());
    }
    private static String profileText(ProfileSnapshot p) {
        var text=new StringBuilder("Structured student-declared profile.\nMajor: ").append(p.majorCode()==null?"not declared":p.majorCode());
        text.append("\nCanonical skills: ").append(!p.skillsDeclared()?"not declared":p.skills().isEmpty()?"declared empty":"declared");
        for(var s:p.skills().stream().sorted(Comparator.comparing(SkillValue::skillCode)).toList())
            text.append("\n").append(s.skillCode()).append("; level: ").append(s.level())
                .append("; practice years: ").append(s.yearsPractice().stripTrailingZeros().toPlainString()).append("; source: ").append(s.source());
        text.append("\nAvailability: ").append(!p.availabilityDeclared()?"not declared":p.availablePeriods().isEmpty()?"declared empty":"declared");
        p.availablePeriods().stream().sorted(Comparator.comparing(v->v.startDate())).forEach(v->text.append("\n").append(v.startDate()).append(" to ").append(v.endDate()));
        return bounded(text.toString());
    }
    private static String bounded(String text) {
        if(text.length()>SemanticProtocol.MAX_TEXT) throw new IllegalArgumentException("SEMANTIC_INPUT_TOO_LARGE");
        return text;
    }
    static String hash(String text) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((SemanticProtocol.TEMPLATE+"\n"+text).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
