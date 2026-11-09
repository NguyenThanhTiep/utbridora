package vn.utbridora.auth.modules.cooperation.integration;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.contracts.ActorContext;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

/** Typed BC31 boundary over the existing Spring seam. No HTTP route accepts this context.
 * Keeps current authentication/authorization in the provider. No SYSTEM bypass or school delegation is inferred.
 */
@Component
public class DemandCriteriaFacade {
    public record ReadRequest(String demandId,Long expectedCriteriaVersion,DemandCriteriaPort.Purpose purpose) {}
    public sealed interface Result permits Success,DomainError {}
    public record Success(DemandCriteriaSnapshot value) implements Result {}
    public record DomainError(String errorCode,List<FieldError> fieldErrors,Integer retryAfterSeconds) implements Result {
        public DomainError { fieldErrors=List.copyOf(fieldErrors); }
    }
    private final DemandCriteriaPort criteria;
    public DemandCriteriaFacade(DemandCriteriaPort criteria) { this.criteria=criteria; }
    public Result read(ActorContext actor,ReadRequest request) {
        if(actor==null) return new DomainError("SESSION_EXPIRED",List.of(),null);
        if(request==null || request.purpose()==null) return new DomainError("SCHEMA_VALIDATION",List.of(),null);
        try { return new Success(criteria.read(actor.authentication(),actor.organizationId(),request.demandId(),request.expectedCriteriaVersion(),request.purpose())); }
        catch(DemandFailure failure) { return new DomainError(failure.code(),failure.fields(),failure.code().equals("REQUEST_IN_PROGRESS")?1:null); }
    }
}
