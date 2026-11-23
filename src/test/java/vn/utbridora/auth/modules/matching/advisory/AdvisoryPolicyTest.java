package vn.utbridora.auth.modules.matching.advisory;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.student.port.StudentSemanticSnapshotPort.*;
import static org.junit.jupiter.api.Assertions.*;

class AdvisoryPolicyTest {
    static Entry entry(int id) {return new Entry("a17e2026-1121-4000-8000-%012d".formatted(id),"a17e2026-1121-4000-8000-%012d".formatted(id+100),1,"a".repeat(64),"a17e2026-1121-4000-8000-%012d".formatted(id+200),"b".repeat(64),"AVAILABLE",List.of(),List.of(new Project("project","Ứng dụng Java","Xây API và SQL","Backend",List.of("JAVA"),"SELF_DECLARED")));}
    static AdvisoryPolicy.Unranked row(int id,int fit,Double cosine) {return new AdvisoryPolicy.Unranked(entry(id),BigDecimal.valueOf(fit),cosine,false,cosine==null?"PROFILE_TEXT_EMPTY":null);}
    @Test void aiActuallyReordersEligibleCandidates() {var result=AdvisoryPolicy.rank(List.of(row(1,90,.6),row(2,70,.8)));assertEquals(entry(2).studentUserId(),result.getFirst().studentUserId());assertEquals(new BigDecimal("70"),result.getFirst().fitScore());}
    @Test void equalQuantizedScoreUsesCanonicalThenUuid() {var result=AdvisoryPolicy.rank(List.of(row(3,90,.60000001),row(2,90,.60000002),row(1,95,.60000003)));assertEquals(List.of(entry(1).studentUserId(),entry(2).studentUserId(),entry(3).studentUserId()),result.stream().map(AdvisoryPolicy.Item::studentUserId).toList());}
    @Test void nullIsNotZeroAndIsAfterNegativeCosine() {var result=AdvisoryPolicy.rank(List.of(row(1,100,null),row(2,1,-.8)));assertEquals(entry(2).studentUserId(),result.getFirst().studentUserId());assertNull(result.getLast().rank());assertNull(result.getLast().semanticScore());assertEquals(2,result.getLast().position());}
    @Test void negativeHalfRoundUsesHalfUp() {assertEquals(new BigDecimal("-0.123457"),AdvisoryPolicy.score(-.1234565));assertEquals(new BigDecimal("0.123457"),AdvisoryPolicy.score(.1234565));}
    @Test void rejectsNonFiniteAndOutsideCosine() {for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,1.01,-1.01})assertThrows(IllegalArgumentException.class,()->AdvisoryPolicy.score(value));}
    @Test void duplicatePopulationIsRejected() {assertThrows(IllegalArgumentException.class,()->AdvisoryPolicy.rank(List.of(row(1,10,.5),row(1,20,.6))));}
    @Test void emptyPopulationHasNoArtificialScore() {assertTrue(AdvisoryPolicy.rank(List.of()).isEmpty());}
    @Test void noContentPreservesCanonicalOrder() {var result=AdvisoryPolicy.rank(List.of(row(1,10,null),row(2,90,null)));assertEquals(entry(2).studentUserId(),result.getFirst().studentUserId());assertTrue(result.stream().allMatch(r->r.rank()==null && r.semanticScore()==null));}
    @Test void unicodeCodepointsAndSurrogateLimitsAgreeWithPython() {assertTrue(AdvisoryPolicy.bounded("😀".repeat(8192)));assertFalse(AdvisoryPolicy.bounded("😀".repeat(8193)));assertFalse(AdvisoryPolicy.bounded("\uD800"));}
    @Test void renderedTextDoesNotSendSourceIdentifiers() {String rendered=AdvisoryPolicy.passage(entry(1));assertTrue(rendered.contains("Ứng dụng Java"));assertTrue(rendered.contains("SELF_DECLARED"));assertFalse(rendered.contains(entry(1).studentUserId()));assertFalse(rendered.contains("sourceId"));assertFalse(rendered.contains("semanticSourceHash"));}
    @Test void globalPositionsSurviveSlicingPages() {var result=AdvisoryPolicy.rank(List.of(row(1,1,.1),row(2,2,.2),row(3,3,.3)));assertEquals(List.of(2,3),result.subList(1,3).stream().map(AdvisoryPolicy.Item::position).toList());}
}
