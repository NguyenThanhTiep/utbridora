package vn.utbridora.auth.modules.matching.semantic;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;

final class SemanticFixtures {
    static final Instant NOW=Instant.parse("2026-11-21T00:00:00Z");
    static String id(long value) {return new UUID(0,value).toString();}
    static final String TOKEN="synthetic_test_only_not_a_credential_123456789";
    static DemandCriteriaSnapshot criteria() {
        return new DemandCriteriaSnapshot(id(1),id(2),id(3),id(4),10,1,1,"VALID",
            new RequiredCriteria(List.of("IT"),List.of(new SkillCriterion("JAVA",2,BigDecimal.ONE)),
                new DateRange(LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-30"))),
            List.of(new SkillCriterion("SQL",null,null)),"criteria-hash","academic-v1","tax-v1",null,null,NOW);
    }
    static ProfileSnapshot profile(int number) {
        return new ProfileSnapshot(id(1000+number),id(2000+number),id(2),id(4),"IT",true,
            List.of(new SkillValue("JAVA",3,BigDecimal.ONE,"SELF_DECLARED")),true,
            List.of(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange("2026-11-01","2026-11-30")),
            1,"profile-hash-"+number,"demo-v1","academic-v1","tax-v1",NOW);
    }
    static SnapshotSet dataset(int count) {return new SnapshotSet(id(5),id(2),id(4),"dataset-version","academic-v1","tax-v1",NOW,count);}
    static List<SemanticProtocol.Pair> scores(List<SemanticProtocol.Passage> batch) {
        return batch.stream().map(p->new SemanticProtocol.Pair(p.key(),"READY",0.82,1,1,false)).toList();
    }
}
