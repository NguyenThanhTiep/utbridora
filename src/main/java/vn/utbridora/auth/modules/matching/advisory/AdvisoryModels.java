package vn.utbridora.auth.modules.matching.advisory;

import java.time.Instant;
import java.util.List;
import vn.utbridora.auth.authorization.AdvisoryAuthority.Stamp;
import vn.utbridora.auth.modules.matching.application.MatchingService.MatchRunRef;
import vn.utbridora.auth.modules.matching.semantic.SemanticProtocol;

public final class AdvisoryModels {
    public record Create(Long expectedDemandVersion,Long expectedCriteriaVersion,String expectedProfileSnapshotVersion) {}
    public record Accepted(String advisoryId,String matchRunId,Instant acceptedAt) {}
    public record Versions(long demandVersion,long criteriaVersion,String profileSnapshotVersion,String modelId,String modelRevision,String templateVersion,String policyVersion,String aggregationVersion) {}
    public record Counts(int eligibleCount,int scoredCount,int noContentCount,int inputTooLargeCount) {}
    public record Metadata(String advisoryId,String matchRunId,String schoolOrgId,String termId,String demandId,String state,Instant createdAt,Instant completedAt,Instant expiresAt,Versions versions,Counts counts,String failureCode) {}
    public record Page(String advisoryId,String matchRunId,String state,String policyVersion,Instant expiresAt,int eligibleCount,int scoredCount,double coverage,int page,int size,int totalElements,int totalPages,List<AdvisoryPolicy.Item> content) {}
    public record Data(String id,MatchRunRef run,String school,String term,String studentSet,String demandSnapshot,Stamp stamp,Instant createdAt,Instant expiresAt,Versions versions,String queryHash,String studentSetHash) {}
    public record Stored(Data data,String state,Instant completedAt,Counts counts,String failure,int attempt,long token,Instant leaseUntil) {}
    public static Versions versions(MatchRunRef r) {return new Versions(r.demandVersion(),r.criteriaVersion(),r.profileSnapshotVersion(),SemanticProtocol.MODEL,SemanticProtocol.REVISION,SemanticProtocol.ADVISORY_TEMPLATE,AdvisoryPolicy.VERSION,SemanticProtocol.AGGREGATION);}
    public static boolean readable(String s) {return s.equals("READY") || s.equals("NO_DATA");}
    private AdvisoryModels() {}
}
