package vn.utbridora.auth.modules.matching.semantic;

import java.net.URI;
import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class SemanticConfiguration {
    @Bean(destroyMethod="close")
    SchoolSemanticObserver schoolSemanticObserver(
            @Value("${app.matching.semantic.enabled:false}") boolean enabled,
            @Value("${app.matching.semantic.base-url:}") String base,
            @Value("${app.matching.semantic.token:}") String token,
            @Value("${app.matching.semantic.model-revision:614241f622f53c4eeff9890bdc4f31cfecc418b3}") String revision,
            @Value("${app.matching.semantic.timeout-ms:12000}") long timeoutMs,
            @Value("${app.matching.semantic.work-timeout-ms:60000}") long workTimeoutMs,
            @Value("${app.matching.semantic.queue-capacity:1}") int queueCapacity,
            @Value("${app.matching.semantic.retained-runs:8}") int retainedRuns,
            @Value("${app.matching.semantic.max-profiles:5000}") int maxProfiles,
            @Value("${app.matching.semantic.result-ttl-seconds:300}") long ttlSeconds) {
        if(!enabled) return SchoolSemanticObserver.disabled();
        if(!SemanticProtocol.REVISION.equals(revision)) throw new IllegalArgumentException("AI revision differs from reviewed model lock");
        var client=new SemanticHttpClient(SemanticHttpClient.baseUri(base),token,revision,Duration.ofMillis(timeoutMs));
        try {return new SchoolSemanticCoordinator(client,revision,queueCapacity,retainedRuns,maxProfiles,
            Duration.ofSeconds(ttlSeconds),Duration.ofMillis(workTimeoutMs),Clock.systemUTC());}
        catch(RuntimeException invalid) {client.close();throw invalid;}
    }
}
