package vn.utbridora.auth.modules.matching.semantic;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.matching.semantic.SemanticFixtures.*;

class SemanticWorkTest {
    @Test void textContainsCanonicalEvidenceButNoDomainIds() {
        var profile=profile(1);var work=SemanticWork.build(id(6),criteria(),dataset(1),List.of(profile));
        String wireText=work.query()+work.items().getFirst().text();
        assertTrue(wireText.contains("JAVA"));assertTrue(wireText.contains("SELF_DECLARED"));
        for(String forbidden:List.of(id(1),id(2),id(3),id(4),profile.studentUserId(),profile.profileSnapshotId())) assertFalse(wireText.contains(forbidden));
        assertEquals("0",work.items().getFirst().key());assertEquals(profile.studentUserId(),work.items().getFirst().ref().studentUserId());
        assertEquals(64,work.queryHash().length());
    }
    @Test void inputHashUsesSemanticContentRatherThanJustIdentityOrVersion() {
        var p=profile(1);
        var changed=new ProfileSnapshot(p.profileSnapshotId(),p.studentUserId(),p.schoolOrgId(),p.termId(),p.majorCode(),true,
            List.of(new SkillValue("JAVA",5,BigDecimal.TEN,"SELF_DECLARED")),p.availabilityDeclared(),p.availablePeriods(),
            p.profileVersion(),p.relevantHash(),p.completenessVersion(),p.academicCatalogVersion(),p.taxonomyVersion(),p.capturedAt());
        var a=SemanticWork.build(id(6),criteria(),dataset(1),List.of(p));
        var b=SemanticWork.build(id(6),criteria(),dataset(1),List.of(changed));
        assertNotEquals(a.items().getFirst().ref().inputHash(),b.items().getFirst().ref().inputHash());
        assertEquals(a.queryHash(),b.queryHash());
    }
    @Test void rejectForeignScopeAndDuplicateProfiles() {
        var p=profile(1);
        var foreign=new ProfileSnapshot(p.profileSnapshotId(),p.studentUserId(),id(99),p.termId(),p.majorCode(),true,p.skills(),true,
            p.availablePeriods(),1,p.relevantHash(),p.completenessVersion(),p.academicCatalogVersion(),p.taxonomyVersion(),p.capturedAt());
        assertThrows(IllegalArgumentException.class,()->SemanticWork.build(id(6),criteria(),dataset(1),List.of(foreign)));
        assertThrows(IllegalArgumentException.class,()->SemanticWork.build(id(6),criteria(),dataset(2),List.of(p,p)));
        assertThrows(IllegalArgumentException.class,()->SemanticWork.build(id(6),criteria(),dataset(2),List.of(p)));
    }
    @Test void workFreezesInputBeforeMutableCallerListChanges() {
        var input=new ArrayList<ProfileSnapshot>();input.add(profile(1));
        var work=SemanticWork.build(id(6),criteria(),dataset(1),input);input.clear();
        assertEquals(1,work.items().size());assertThrows(UnsupportedOperationException.class,()->work.items().clear());
    }
}
