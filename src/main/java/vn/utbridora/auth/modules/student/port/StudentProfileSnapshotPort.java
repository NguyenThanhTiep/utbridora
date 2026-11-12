package vn.utbridora.auth.modules.student.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.DateRange;

public interface StudentProfileSnapshotPort {
    record SkillValue(String skillCode,int level,BigDecimal yearsPractice,String source) {}
    record ProfileSnapshot(String profileSnapshotId,String studentUserId,String schoolOrgId,String termId,String majorCode,
        boolean skillsDeclared,List<SkillValue> skills,boolean availabilityDeclared,List<DateRange> availablePeriods,
        long profileVersion,String relevantHash,String completenessVersion,String academicCatalogVersion,String taxonomyVersion,Instant capturedAt,
        // liên quan đến việc thêm lọc svien theo gpa là MVP: Snapshot giữ GPA cùng phiên bản hồ sơ dùng cho matching.
        BigDecimal gpa) {
        public ProfileSnapshot(String profileSnapshotId,String studentUserId,String schoolOrgId,String termId,String majorCode,
                boolean skillsDeclared,List<SkillValue> skills,boolean availabilityDeclared,List<DateRange> availablePeriods,
                long profileVersion,String relevantHash,String completenessVersion,String academicCatalogVersion,String taxonomyVersion,Instant capturedAt) {
            this(profileSnapshotId,studentUserId,schoolOrgId,termId,majorCode,skillsDeclared,skills,availabilityDeclared,availablePeriods,
                profileVersion,relevantHash,completenessVersion,academicCatalogVersion,taxonomyVersion,capturedAt,null);
        }
    }
    record SnapshotSet(String datasetSnapshotId,String schoolOrgId,String termId,String profileSnapshotVersion,
        String academicCatalogVersion,String taxonomyVersion,Instant capturedAt,int profileCount) {}
    record SnapshotPage(String datasetSnapshotId,String profileSnapshotVersion,List<ProfileSnapshot> items,String nextCursor) {}
    record DecisionProfileRef(String studentUserId,String profileSnapshotId,long profileVersion,String relevantHash) {}
    SnapshotSet captureForMatching(Authentication actor,String termId,String academicVersion,String taxonomyVersion,String key);
    SnapshotPage readPage(Authentication actor,String datasetSnapshotId,String cursor,int size);
    ProfileSnapshot readSnapshot(Authentication actor,String profileSnapshotId);
    void assertCurrentForDecision(Authentication actor,List<DecisionProfileRef> refs);
}
