package vn.utbridora.auth.modules.matching.semantic;

import java.time.Duration;
import java.util.List;

/** Backend-to-AI contract only: no domain identifiers or principal are sent to Python. */
public final class SemanticProtocol {
    private SemanticProtocol() {}
    public static final String SCHEMA="semantic-v1", TEMPLATE="canonical-v1";
    public static final String ADVISORY_TEMPLATE="school-advisory-text-v1";
    public static final String MODEL="intfloat/multilingual-e5-small";
    public static final String REVISION="614241f622f53c4eeff9890bdc4f31cfecc418b3";
    public static final String AGGREGATION="token-weighted-mean-v1";
    public static final int DIMENSION=384, MAX_TEXT=8192, MAX_BATCH=16;
    public record Passage(String key,String text) {}
    public record Request(String schemaVersion,String requestId,String templateVersion,String query,List<Passage> passages) {}
    public record Pair(String key,String status,Double score,Integer queryChunks,Integer passageChunks,Boolean limited) {}
    public record Response(String schemaVersion,String requestId,String templateVersion,String modelId,
                           String modelRevision,Integer dimension,String aggregationVersion,List<Pair> results) {}
    @FunctionalInterface public interface Client {
        List<Pair> compare(String query,List<Passage> passages,Duration remaining);
    }
    public static final class Unavailable extends RuntimeException {
        public Unavailable(String safeCode) { super(safeCode,null,false,false); }
    }
}
