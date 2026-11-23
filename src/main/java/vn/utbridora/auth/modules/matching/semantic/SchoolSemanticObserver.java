package vn.utbridora.auth.modules.matching.semantic;

import java.util.List;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandCriteriaSnapshot;
import vn.utbridora.auth.modules.student.port.StudentProfileSnapshotPort.*;

/** Internal school-only shadow seam. No public DTO, authority, decision or persistence API. */
public interface SchoolSemanticObserver extends AutoCloseable {
    boolean enabled();
    int captureLimit();
    void afterSchoolCommit(String runId, DemandCriteriaSnapshot criteria, SnapshotSet dataset,
                           List<ProfileSnapshot> profiles);
    @Override default void close() {}

    static SchoolSemanticObserver disabled() {
        return new SchoolSemanticObserver() {
            public boolean enabled() { return false; }
            public int captureLimit() { return 0; }
            public void afterSchoolCommit(String id, DemandCriteriaSnapshot c, SnapshotSet d, List<ProfileSnapshot> p) {}
        };
    }
}
