package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.files.FileBindingChecks;
import vn.utbridora.auth.modules.cooperation.api.CooperationDtos.*;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.persistence.*;
import vn.utbridora.auth.modules.cooperation.port.OrganizationSchoolVerificationPort;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.organization.*;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;

/** CP-T5UI1: canonical attempts, current scope, immutable submission history and one atomic command. */
@Service
public class VerificationService implements OrganizationSchoolVerificationPort {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final List<VerificationState> OCCUPIED=List.of(VerificationState.DRAFT,VerificationState.SUBMITTED,VerificationState.NEEDS_CHANGES,VerificationState.APPROVED);
    private static final Set<String> FIELDS=Set.of("registrationCode","registeredName","address","contactName","contactEmail","contactPhone","evidenceFileIds");
    private final VerificationRepository records;
    private final VerificationHistoryRepository history;
    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final AuthorizationPolicy access;
    private final SchoolDelegationAccess delegations;
    private final OperationLedger operations;
    private final FileBindingChecks files;
    private final AuditService audit;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    public VerificationService(VerificationRepository records,VerificationHistoryRepository history,OrganizationRepository organizations,
        UserRepository users,AuthorizationPolicy access,SchoolDelegationAccess delegations,OperationLedger operations,FileBindingChecks files,AuditService audit) {
        this.records=records;this.history=history;this.organizations=organizations;this.users=users;this.access=access;
        this.delegations=delegations;this.operations=operations;this.files=files;this.audit=audit;
    }
    @Transactional public VerificationView create(Authentication auth,String organization,String key,VerificationInput data) {
        String org=uuid(organization),actor=enterprise(auth,org,true),school=uuid(data.schoolOrgId());
        return operations.execute(new OperationLedger.Command(actor,org,"VERIFICATION_CREATE",key,data,201),VerificationView.class,()->{
            requireSchool(school);
            if(records.existsByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(org,school,OCCUPIED)) throw failure(409,"VERIFICATION_ALREADY_OPEN");
            String previous=data.previousVerificationId()==null?null:uuid(data.previousVerificationId());
            if(previous==null && records.existsByEnterpriseOrgIdAndSchoolOrgIdAndStateIn(org,school,List.of(VerificationState.REJECTED))) throw failure(422,"VERIFICATION_DATA_INVALID");
            if(previous!=null) {
                var old=find(previous,org,false,false);
                if(!school.equals(old.schoolOrgId) || old.state!=VerificationState.REJECTED) throw failure(422,"VERIFICATION_DATA_INVALID");
            }
            EnterpriseVerification row=new EnterpriseVerification();row.enterpriseOrgId=org;row.schoolOrgId=school;row.previousVerificationId=previous;
            apply(row,data);row=records.saveAndFlush(row);
            files.requireVerificationEvidence(actor,org,row.id,evidence(row));
            changed(actor,row,null,null,"ENTERPRISE_VERIFICATION_DRAFT_SAVED");return view(row);
        },VerificationView::verificationId);
    }
    @Transactional public VerificationView update(Authentication auth,String organization,String id,String key,UpdateVerificationData request) {
        String org=uuid(organization),actor=enterprise(auth,org,true),recordId=uuid(id);
        return operations.execute(new OperationLedger.Command(actor,org,"VERIFICATION_UPDATE",key,Map.of("id",recordId,"data",request),200),VerificationView.class,()->{
            var row=find(recordId,org,false,true);version(row,request.expectedVersion());editable(row);
            var data=request.data();
            if(!row.schoolOrgId.equals(uuid(data.schoolOrgId())) || !Objects.equals(row.previousVerificationId,data.previousVerificationId()==null?null:uuid(data.previousVerificationId()))) throw failure(422,"VERIFICATION_DATA_INVALID");
            requireSchool(row.schoolOrgId);files.requireVerificationEvidence(actor,org,row.id,data.evidenceFileIds());
            apply(row,data);records.saveAndFlush(row);changed(actor,row,row.state,null,"ENTERPRISE_VERIFICATION_DRAFT_SAVED");return view(row);
        },VerificationView::verificationId);
    }
    @Transactional public VerificationView submit(Authentication auth,String organization,String id,String key,SubmitVerification request) {
        String org=uuid(organization),actor=enterprise(auth,org,true),recordId=uuid(id);
        return operations.execute(new OperationLedger.Command(actor,org,"VERIFICATION_SUBMIT",key,Map.of("id",recordId,"data",request),200),VerificationView.class,()->{
            var row=find(recordId,org,false,true);version(row,request.expectedVersion());editable(row);requireSchool(row.schoolOrgId);
            if(evidence(row).isEmpty()) throw failure(422,"VERIFICATION_DATA_INVALID");
            files.requireVerificationEvidence(actor,org,row.id,evidence(row));
            if(row.submissionNo==Integer.MAX_VALUE) throw failure(409,"STATE_CONFLICT");
            var before=row.state;row.state=VerificationState.SUBMITTED;row.submissionNo++;row.submitterId=actor;row.submittedAt=Instant.now();
            row.reviewerId=null;row.reviewedAt=null;row.decisionRef=null;row.missingFields="[]";
            records.saveAndFlush(row);changed(actor,row,before,null,"ENTERPRISE_VERIFICATION_SUBMITTED");return view(row);
        },VerificationView::verificationId);
    }
    @Transactional public VerificationView decide(Authentication auth,String organization,String id,String key,VerificationDecision request) {
        String school=uuid(organization),actor=actor(auth,true),recordId=uuid(id);
        delegations.require(auth,school,null,"enterprise_verification:school:review");
        return operations.execute(new OperationLedger.Command(actor,school,"VERIFICATION_DECIDE",key,Map.of("id",recordId,"data",request),200),VerificationView.class,()->{
            var row=find(recordId,school,true,true);version(row,request.expectedVersion());
            if(row.state!=VerificationState.SUBMITTED) throw failure(409,"STATE_CONFLICT");
            if(request.decision()==DecisionType.APPROVE) files.requireVerificationEvidence(row.submitterId,row.enterpriseOrgId,row.id,evidence(row));
            if(request.missingFields().size()!=new HashSet<>(request.missingFields()).size() || !FIELDS.containsAll(request.missingFields())) throw failure(422,"VERIFICATION_DATA_INVALID");
            if(request.decision()!=DecisionType.APPROVE && request.reasonCode()==null
                || request.decision()==DecisionType.REQUEST_INFO && request.missingFields().isEmpty()
                || request.decision()!=DecisionType.REQUEST_INFO && !request.missingFields().isEmpty()) throw failure(422,"VERIFICATION_DATA_INVALID");
            var before=row.state;row.state=switch(request.decision()) {case APPROVE->VerificationState.APPROVED;case REJECT->VerificationState.REJECTED;case REQUEST_INFO->VerificationState.NEEDS_CHANGES;};
            row.reviewerId=actor;row.reviewedAt=Instant.now();row.decisionRef=request.decisionRef();row.missingFields=JSON.writeValueAsString(request.missingFields());
            String action=switch(request.decision()) {case APPROVE->"ENTERPRISE_VERIFIED";case REJECT->"ENTERPRISE_VERIFICATION_REJECTED";case REQUEST_INFO->"ENTERPRISE_VERIFICATION_CHANGES_REQUESTED";};
            records.saveAndFlush(row);changed(actor,row,before,request.reasonCode(),action);return view(row);
        },VerificationView::verificationId);
    }
    @Transactional(readOnly=true) public Page<VerificationView> list(Authentication auth,String organization,boolean schoolSide,String schoolFilter,VerificationState state,int page,int size) {
        String org=uuid(organization);
        if(schoolSide) {actor(auth,false);delegations.require(auth,org,null,"enterprise_verification:school:review");}
        else enterprise(auth,org,false);
        if(page<0 || page>10000 || size<1 || size>100) throw failure(400,"INVALID_PAGINATION");
        String filter=schoolFilter==null?null:uuid(schoolFilter);
        if(schoolSide && filter!=null && !org.equals(filter)) throw missing();
        Specification<EnterpriseVerification> scope=(r,q,b)->b.equal(r.get(schoolSide?"schoolOrgId":"enterpriseOrgId"),org);
        if(filter!=null) scope=scope.and((r,q,b)->b.equal(r.get("schoolOrgId"),filter));
        if(state!=null) scope=scope.and((r,q,b)->b.equal(r.get("state"),state));
        var result=records.findAll(scope,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by("id"))));
        return new Page<>(result.stream().map(this::view).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @Transactional(readOnly=true) public VerificationView detail(Authentication auth,String organization,String id,boolean schoolSide) {
        String org=uuid(organization);
        if(schoolSide) {actor(auth,false);delegations.require(auth,org,null,"enterprise_verification:school:review");}
        else enterprise(auth,org,false);
        return view(find(uuid(id),org,schoolSide,false));
    }
    @Override @Transactional(readOnly=true) public ReadResult get(ReadContext context,String enterpriseOrgId,String schoolOrgId) {
        if(context==null || context.purpose==null || context.termId==null) return new DomainError("ACCESS_DENIED");
        try {
            actor(context.actor,false);String school=uuid(schoolOrgId),enterprise=uuid(enterpriseOrgId),term=uuid(context.termId);
            String permission=context.purpose==Purpose.CONFIRM_COORDINATOR?"t3:coordinator:confirm":"placement:school:write";
            delegations.require(context.actor,school,term,permission);
            return new Success(records.findFirstByEnterpriseOrgIdAndSchoolOrgIdOrderByCreatedAtDescIdAsc(enterprise,school)
                .map(row->{
                    if(!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly()) entityManager.refresh(row,jakarta.persistence.LockModeType.PESSIMISTIC_READ);
                    return new SchoolVerificationView(enterprise,school,row.id,row.state.name(),row.version,row.state==VerificationState.APPROVED?row.reviewedAt:null,row.state==VerificationState.APPROVED);
                })
                .orElseGet(()->new SchoolVerificationView(enterprise,school,null,null,null,null,false)));
        } catch(OperationFailure failure) {return new DomainError(failure.code);}
    }
    private void changed(String actor,EnterpriseVerification row,VerificationState from,String reason,String action) {
        VerificationHistory entry=new VerificationHistory();entry.verificationId=row.id;entry.fromState=from==null?null:from.name();entry.toState=row.state.name();entry.actorId=actor;
        entry.reasonCode=reason;entry.missingFields=row.missingFields;entry.decisionRef=row.decisionRef;entry.submissionNo=row.submissionNo;entry.snapshotJson=JSON.writeValueAsString(view(row));history.save(entry);
        try {audit.verificationChanged(actor,row.id,action,row.schoolOrgId,row.submissionNo,row.version,reason,row.decisionRef,missingFields(row));entityManager.flush();}
        catch(RuntimeException failure) {throw failure(500,"AUDIT_WRITE_FAILED");}
    }
    private VerificationView view(EnterpriseVerification row) {return new VerificationView(row.id,row.enterpriseOrgId,row.schoolOrgId,row.state,row.submissionNo,row.version,row.previousVerificationId,
        new VerificationInput(row.schoolOrgId,row.previousVerificationId,row.registrationCode,row.registeredName,row.address,row.contactName,row.contactEmail,row.contactPhone,evidence(row)),row.submittedAt,row.reviewedAt,row.decisionRef,missingFields(row));}
    private static List<String> list(String value) {return JSON.readValue(value,JSON.getTypeFactory().constructCollectionType(List.class,String.class));}
    private static List<String> evidence(EnterpriseVerification row) {return list(row.evidenceFileIdsJson);}
    private static List<String> missingFields(EnterpriseVerification row) {return list(row.missingFields);}
    private static void apply(EnterpriseVerification row,VerificationInput data) {
        if(data.evidenceFileIds().size()!=new HashSet<>(data.evidenceFileIds()).size()) throw failure(422,"VERIFICATION_DATA_INVALID");
        row.registrationCode=data.registrationCode();row.registeredName=data.registeredName();row.address=data.address();row.contactName=data.contactName();row.contactEmail=data.contactEmail();row.contactPhone=data.contactPhone();row.evidenceFileIdsJson=JSON.writeValueAsString(data.evidenceFileIds());
    }
    private EnterpriseVerification find(String id,String org,boolean school,boolean locked) {return (locked?records.findByIdForUpdate(id):records.findById(id)).filter(row->org.equals(school?row.schoolOrgId:row.enterpriseOrgId)).orElseThrow(VerificationService::missing);}
    private static void editable(EnterpriseVerification row) {if(row.state!=VerificationState.DRAFT && row.state!=VerificationState.NEEDS_CHANGES) throw failure(409,"STATE_CONFLICT");}
    private static void version(EnterpriseVerification row,long expected) {if(row.version!=expected || row.version>=9_007_199_254_740_991L) throw failure(409,"VERSION_CONFLICT");}
    private void requireSchool(String id) {if(organizations.findById(id).filter(o->o.type==Organization.Type.UNIVERSITY && o.status==Organization.Status.VERIFIED).isEmpty()) throw failure(422,"VERIFICATION_DATA_INVALID");}
    private String enterprise(Authentication auth,String org,boolean lock) {String id=actor(auth,lock);if(!access.hasOrganization(auth,org,Role.ENTERPRISE,"enterprise_verification:enterprise:manage")) throw failure(403,"ACCESS_DENIED");return id;}
    private String actor(Authentication auth,boolean lock) {
        if(auth==null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal p)) throw failure(401,"SESSION_EXPIRED");
        var current=lock?users.findByIdForUpdate(p.userId()):users.findById(p.userId());
        if(current.filter(u->u.status==AccountStatus.ACTIVE && u.securityVersion==p.securityVersion()).isEmpty()) throw failure(401,"SESSION_EXPIRED");return p.userId();
    }
    private static String uuid(String value) {if(value==null || !value.matches(vn.utbridora.auth.modules.cooperation.api.CooperationDtos.UUID_PATTERN)) throw failure(400,"SCHEMA_VALIDATION");return value.toLowerCase(Locale.ROOT);}
    private static OperationFailure missing() {return failure(404,"RESOURCE_NOT_FOUND");}
    private static OperationFailure failure(int status,String code) {return new OperationFailure(status,code,"Không thể thực hiện thao tác với dữ liệu, trạng thái hoặc phạm vi hiện tại.",null);}
}
