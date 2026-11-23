package vn.utbridora.auth.modules.student.port;

import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;

/** T2-owned filtered snapshots. Actor and refs are constructed inside trusted workflows. */
public interface StudentSemanticSnapshotPort {
    record Skill(String sourceId,String kind,String codeOrName,String categoryCode,int level,String yearsPractice,String note,String source) {}
    record Project(String sourceId,String title,String description,String role,List<String> skillCodes,String source) {}
    record Entry(String studentUserId,String profileSnapshotId,long profileVersion,String relevantHash,String semanticSnapshotId,String semanticSourceHash,String sourceStatus,List<Skill> skills,List<Project> projects) {}
    record SetRef(String setId,String sourceSetHash,List<String> allowedFields,Instant capturedAt,Instant validUntil,int count,String school,String term,String academicVersion,String taxonomyVersion) {}
    SetRef capture(Authentication auth,String school,String term,String datasetId,String taxonomy,List<StudentProfileSnapshotPort.DecisionProfileRef> refs,Instant until);
    SetRef read(Authentication auth,String id);
    List<Entry> page(Authentication auth,String id,String after,int size);
    boolean current(Authentication auth,String id);
    void purge(String id);
}
