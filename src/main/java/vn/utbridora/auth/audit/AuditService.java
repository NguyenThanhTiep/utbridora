package vn.utbridora.auth.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Strict allow-list fields only. No request body, token, credentials, or raw query string in audit. */
@Service
public class AuditService {
    private final AuditRepository repository;
    public AuditService(AuditRepository repository){this.repository=repository;}
    @Transactional
    public void write(String actor,String action,String resourceType,String resourceId,String result){
        writeScoped(actor,action,resourceType,resourceId,result,null,null);
    }
    @Transactional
    public void writeScoped(String actor,String action,String resourceType,String resourceId,
                            String result,String organizationId,String reasonCode) {
        store(actor,action,resourceType,resourceId,result,organizationId,reasonCode);
    }
    /** Denial events survive rollback of the failing business transaction. */
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void securityDenied(String actor,String reasonCode) {
        store(actor,"ACCESS_DENIED","http_request",null,"DENIED",null,reasonCode);
    }
    @Transactional
    public void taxonomyChanged(String actor,String id,String entityType,String operation,String skillCode,String taxonomyVersion) {
        if(!java.util.Set.of("CATEGORY","SKILL","ALIAS").contains(entityType)
            || !java.util.Set.of("CREATE","UPDATE","DEPRECATE","DELETE_ALIAS").contains(operation)
            || skillCode!=null && !skillCode.matches("[A-Z][A-Z0-9_]{0,63}")
            || taxonomyVersion==null || !taxonomyVersion.matches("skills-v[1-9][0-9]*")) throw new IllegalArgumentException("Invalid taxonomy audit metadata");
        var metadata=new java.util.LinkedHashMap<String,Object>();
        metadata.put("entityType",entityType);metadata.put("operation",operation);metadata.put("skillCode",skillCode);metadata.put("taxonomyVersion",taxonomyVersion);
        var event=store(actor,"TAXONOMY_CHANGED","skill_taxonomy_entry",id,"SUCCESS",null,operation);
        event.metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(metadata);
    }
    /** Typed allowlist for Demand; callers cannot inject arbitrary metadata or payloads. */
    @Transactional
    public void demandChanged(String actor,String demandId,String org,String operation,long demandVersion,long criteriaVersion) {
        if(!java.util.Set.of("CREATE","UPDATE","REOPEN","SOFT_DELETE","RESTORE").contains(operation)
                || demandVersion<1 || criteriaVersion<1 || criteriaVersion>demandVersion
                || demandVersion>9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid demand audit metadata");
        var event=store(actor,"DEMAND_CHANGED","demand",demandId,"SUCCESS",org,"DEMAND_"+operation);
        event.metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of(
            "operation",operation,"demandVersion",demandVersion,"criteriaVersion",criteriaVersion));
    }
    @Transactional
    public void verificationChanged(String actor,String id,String action,String school,int submission,long version,
                                    String reason,String decisionRef,java.util.List<String> missing) {
        if(!java.util.Set.of("ENTERPRISE_VERIFICATION_DRAFT_SAVED","ENTERPRISE_VERIFICATION_SUBMITTED",
            "ENTERPRISE_VERIFICATION_REJECTED","ENTERPRISE_VERIFICATION_CHANGES_REQUESTED","ENTERPRISE_VERIFIED").contains(action)
            || submission<0 || version<1 || version>9_007_199_254_740_991L
            || decisionRef!=null && (decisionRef.isBlank() || decisionRef.length()>120)
            || missing.stream().anyMatch(field->!java.util.Set.of("registrationCode","registeredName","address","contactName","contactEmail","contactPhone","evidenceFileIds").contains(field)))
            throw new IllegalArgumentException("Invalid verification audit metadata");
        validateId(school);
        var data=new java.util.LinkedHashMap<String,Object>();data.put("schoolOrgId",school);data.put("submissionNo",submission);data.put("version",version);
        if(action.equals("ENTERPRISE_VERIFIED")) data.put("decisionRef",decisionRef);
        if(action.equals("ENTERPRISE_VERIFICATION_REJECTED") || action.equals("ENTERPRISE_VERIFICATION_CHANGES_REQUESTED")) data.put("reasonCode",reason);
        if(action.equals("ENTERPRISE_VERIFICATION_CHANGES_REQUESTED")) data.put("missingFieldCodes",missing);
        store(actor,action,"enterprise_verification",id,"SUCCESS",school,reason).metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
    }
    @Transactional
    public void placementPublished(String actor,vn.utbridora.auth.contracts.placement.PlacementReadPort.PlacementView p,String operation,String reason) {
        validateId(operation);validateId(p.termId());
        String action=switch(p.state()) {case "APPROVED"->"PLACEMENT_APPROVED";case "ACTIVE"->"PLACEMENT_ACTIVATED";
            case "CANCELLED"->"PLACEMENT_CANCELLED";default->throw new IllegalArgumentException("Closure proof required for completed placement");};
        var data=new java.util.LinkedHashMap<String,Object>();data.put("termId",p.termId());data.put("placementVersion",p.version());data.put("operationId",operation);
        if(p.state().equals("ACTIVE")) data.put("effectiveAt",p.effectiveAt().toString());
        else data.put("decisionRef",p.decisionRef());
        if(p.state().equals("CANCELLED")) data.put("reasonCode",reason);
        store(actor,action,"placement",p.placementId(),"SUCCESS",p.schoolOrgId(),reason).metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
        flushRequiredAudit();
    }
    @Transactional
    public void cohortCreated(String actor,String id,String enterprise,String school,String term,long version) {
        validateId(enterprise);validateId(term);
        store(actor,"COHORT_CREATED","internship_cohort",id,"SUCCESS",school,null).metadataJson=
            new tools.jackson.databind.ObjectMapper().writeValueAsString(java.util.Map.of("enterpriseOrgId",enterprise,"termId",term,"cohortVersion",version));
        flushRequiredAudit();
    }
    @Transactional
    public void groupMembershipChanged(String actor,String caseId,String school,String cohort,String from,String to,long version,String reason) {
        validateId(cohort);validateId(from);validateId(to);
        var data=new java.util.LinkedHashMap<String,Object>();data.put("cohortId",cohort);data.put("fromGroupId",from);data.put("toGroupId",to);data.put("version",version);data.put("reasonCode",reason);
        store(actor,"GROUP_MEMBERSHIP_CHANGED","internship_case",caseId,"SUCCESS",school,reason).metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(data);
        flushRequiredAudit();
    }
    @Transactional
    public void taskAssigned(String actor,String assignment,String school,String task,String caseId,String group,long version) {
        validateId(task);validateId(caseId);validateId(group);
        store(actor,"TASK_ASSIGNED","task_assignment",assignment,"SUCCESS",school,null).metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(
            java.util.Map.of("taskId",task,"caseId",caseId,"groupId",group,"assignmentVersion",version));
        flushRequiredAudit();
    }
    /** Registry-checked metadata for new owner workflows; SUCCESS joins the domain transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void workflow(String actor,String action,String id,String org,java.util.Map<String,Object> metadata) {
        var spec=WorkflowAuditRegistry.ACTIONS.get(action);
        if(spec==null || !spec.fields().containsAll(metadata.keySet())) throw new IllegalArgumentException("Invalid workflow audit metadata");
        var event=store(actor,action,spec.resourceType(),id,"SUCCESS",org,null);
        event.metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(metadata);
        flushRequiredAudit();
    }
    /** Successful matching execution audit; MANDATORY keeps the record atomic with the matching transaction. */
    @Transactional(propagation=Propagation.MANDATORY)
    public void matchingRunCompleted(String actor,String demandId,String matchRunId,String organizationId,
                                     java.util.Map<String,Object> metadata) {
        var spec=WorkflowAuditRegistry.ACTIONS.get("MATCH_RUN_COMPLETED");
        if(!spec.fields().containsAll(metadata.keySet())) throw new IllegalArgumentException("Invalid matching audit metadata");
        validateId(demandId);
        var event=store(actor,"MATCH_RUN_COMPLETED",spec.resourceType(),matchRunId,"SUCCESS",organizationId,null);
        event.demandId=demandId;
        event.ipAddress=requestIpAddress();
        event.metadataJson=new tools.jackson.databind.ObjectMapper().writeValueAsString(metadata);
        flushRequiredAudit();
    }
    private void flushRequiredAudit() {
        try {repository.flush();}
        catch(org.springframework.dao.DataAccessException failure) {
            throw new vn.utbridora.auth.operations.OperationFailure(500,"AUDIT_WRITE_FAILED","Không thể lưu nhật ký; thao tác chưa được thực hiện.",null);
        }
    }
    private AuditLog store(String actor,String action,String resourceType,String resourceId,
                       String result,String organizationId,String reasonCode) {
        AuditAction.valueOf(action);
        if(resourceType==null || !resourceType.matches("[a-z][a-z0-9_]{0,79}")
            || !java.util.Set.of("SUCCESS","FAILURE","DENIED").contains(result)
            || reasonCode!=null && !reasonCode.matches("[A-Z0-9_]{3,64}"))
            throw new IllegalArgumentException("Invalid audit metadata");
        validateId(actor);validateId(resourceId);validateId(organizationId);
        AuditLog event=new AuditLog();event.actorId=actor;event.action=action;
        event.resourceType=resourceType;event.resourceId=resourceId;event.result=result;
        event.organizationId=organizationId;event.reasonCode=reasonCode;
        event.requestId=org.slf4j.MDC.get("requestId");
        var attributes=RequestContextHolder.getRequestAttributes();
        if(attributes instanceof ServletRequestAttributes web) {
            HttpServletRequest req=web.getRequest();
            Object id=req.getAttribute("bridora.request_id");
            if(id instanceof String s) event.requestId=s;
        }
        if(event.requestId==null) event.requestId=java.util.UUID.randomUUID().toString();
        return repository.save(event);
    }
    private String requestIpAddress() {
        var attributes=RequestContextHolder.getRequestAttributes();
        if(attributes instanceof ServletRequestAttributes web) {
            String address=web.getRequest().getRemoteAddr();
            if(address!=null && !address.isBlank() && address.length()<=45) return address;
        }
        return null;
    }
    private void validateId(String id) {
        if(id!=null && !id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new IllegalArgumentException("Audit IDs must be UUID values");
    }
}
