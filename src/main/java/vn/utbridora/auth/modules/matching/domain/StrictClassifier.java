package vn.utbridora.auth.modules.matching.domain;

import java.math.*;
import java.time.LocalDate;
import java.util.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

/** Deterministic Strict matching; only exact owner-provided aliases are normalized. */
// DEV8 đã sửa từ xếp hạng mọi trạng thái theo fitScore thành chỉ dùng fitScore cho MATCH,
// các trạng thái còn lại không có điểm và xếp ổn định theo studentUserId, phục vụ R11.
public final class StrictClassifier {
    public enum Status {PASS,FAIL,UNKNOWN}
    public record CriterionEvaluation(String criterionCode,String type,String kind,Status status,String reasonCode) {}
    public record MatchResult(String studentUserId,String profileSnapshotId,long profileVersion,String relevantHash,
        String overallStatus,BigDecimal fitScore,List<CriterionEvaluation> details) {}
    public static final String RULE_VERSION="strict-v1",SCORING_VERSION="preferred-v1";
    private StrictClassifier() {}
    public static MatchResult evaluate(ProfileSnapshot profile,RequiredCriteria required,List<SkillCriterion> preferred,Map<String,String> aliases) {
        var details=new ArrayList<CriterionEvaluation>();
        Status major=required.majorCodes().isEmpty()?Status.PASS:profile.majorCode()==null?Status.UNKNOWN:
            required.majorCodes().contains(profile.majorCode())?Status.PASS:Status.FAIL;
        details.add(detail("MAJOR","REQUIRED","MAJOR",major));
        Map<String,SkillValue> skills=new HashMap<>();
        for(var skill:profile.skills()) skills.put(aliases.getOrDefault(skill.skillCode(),skill.skillCode()),skill);
        for(var criterion:required.skills()) details.add(detail(criterion.skillCode(),"REQUIRED","SKILL",skill(profile,skills,criterion,aliases)));
        Status availability=profile.availabilityDeclared()?covers(profile,required.dateRange())?Status.PASS:Status.FAIL:Status.UNKNOWN;
        details.add(detail("AVAILABILITY","REQUIRED","AVAILABILITY",availability));
        String overall=details.stream().anyMatch(d->d.status()==Status.FAIL)?"NOT_MATCH":details.stream().anyMatch(d->d.status()==Status.UNKNOWN)?"MISSING_DATA":"MATCH";
        long passed=0;
        for(var criterion:preferred) {
            Status status=skill(profile,skills,criterion,aliases);if(status==Status.PASS) passed++;
            details.add(detail(criterion.skillCode(),"PREFERRED","SKILL",status));
        }
        BigDecimal score=!overall.equals("MATCH")?null:preferred.isEmpty()?BigDecimal.ZERO.setScale(2):
            BigDecimal.valueOf(passed*100).divide(BigDecimal.valueOf(preferred.size()),2,RoundingMode.HALF_UP);
        return new MatchResult(profile.studentUserId(),profile.profileSnapshotId(),profile.profileVersion(),profile.relevantHash(),overall,score,List.copyOf(details));
    }
    public static Comparator<MatchResult> ranking() {
        return Comparator.comparing((MatchResult result) -> "MATCH".equals(result.overallStatus()) ? result.fitScore() : null,
            Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(MatchResult::studentUserId);
    }
    private static Status skill(ProfileSnapshot profile,Map<String,SkillValue> skills,SkillCriterion c,Map<String,String> aliases) {
        if(!profile.skillsDeclared()) return Status.UNKNOWN;
        SkillValue s=skills.get(aliases.getOrDefault(c.skillCode(),c.skillCode()));
        if(s==null) return Status.FAIL;
        return (c.minLevel()==null || s.level()>=c.minLevel())
            && (c.minYearsPractice()==null || s.yearsPractice().compareTo(c.minYearsPractice())>=0)?Status.PASS:Status.FAIL;
    }
    private static boolean covers(ProfileSnapshot p,DateRange required) {
        LocalDate next=required.startDate();
        var ranges=p.availablePeriods().stream().sorted(Comparator.comparing(v->LocalDate.parse(v.startDate()))).toList();
        for(var r:ranges) {
            LocalDate start=LocalDate.parse(r.startDate()),end=LocalDate.parse(r.endDate());
            if(start.isAfter(next)) return false;
            if(!end.isBefore(next)) {
                if(!end.isBefore(required.endDate())) return true;
                next=end.plusDays(1);
            }
        }
        return false;
    }
    private static CriterionEvaluation detail(String code,String type,String kind,Status status) {
        return new CriterionEvaluation(code,type,kind,status,status==Status.PASS?"SATISFIED":status==Status.FAIL?"REQUIRED_NOT_SATISFIED":"DATA_NOT_DECLARED");
    }
}
