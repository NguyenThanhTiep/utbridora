package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.domain.*;
import vn.utbridora.auth.modules.cooperation.integration.*;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository.StoredDemand;
import vn.utbridora.auth.security.AppPrincipal;
import vn.utbridora.auth.user.*;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

@Service
public class DemandService implements DemandCriteriaPort {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final DemandRepository repository;
    private final DemandReferencePort references;
    private final AuthorizationPolicy authorization;
    private final UserRepository users;
    private final AuditService audit;
    private final boolean contractApproved;
    public DemandService(DemandRepository repository,DemandReferencePort references,AuthorizationPolicy authorization,
            UserRepository users,AuditService audit,@Value("${app.t5.demand.contract-approved:false}") boolean contractApproved) {
        this.repository=repository; this.references=references; this.authorization=authorization;
        this.users=users; this.audit=audit; this.contractApproved=contractApproved;
    }
    @Transactional
    public DemandDto create(Authentication authentication,String organization,String key,DemandInput request) {
        String org=DemandValidator.uuid(organization,"X-Organization-Id"), commandKey=DemandValidator.uuid(key,"Idempotency-Key");
        String actor=authorize(authentication,org); DemandInput input=DemandValidator.validate(request);
        Instant now=Instant.now(); var command=repository.lockCommand(actor,org,"CREATE",commandKey,DemandJson.requestHash(request),now);
        DemandDto replay=replay(command,DemandJson.requestHash(request),now); if(replay!=null) return replay;
        references.validate(org,input);
        var demand=new StoredDemand(UUID.randomUUID().toString(),org,"DRAFT",1,1,DemandJson.criteriaHash(input),input,now,now,null);
        repository.insert(demand); repository.addRevision(demand,snapshot(demand),actor,"CREATE"); audit(actor,demand,"DEMAND_CREATE");
        repository.completeCommand(actor,org,"CREATE",commandKey,demand.dto()); return demand.dto();
    }
    @Transactional
    public DemandDto update(Authentication authentication,String organization,String id,String key,UpdateDemand request) {
        String org=DemandValidator.uuid(organization,"X-Organization-Id"), demandId=DemandValidator.uuid(id,"demandId");
        String commandKey=DemandValidator.uuid(key,"Idempotency-Key"); String actor=authorize(authentication,org);
        long expected=DemandValidator.version(request.expectedVersion()); DemandInput input=DemandValidator.validate(request.data());
        String hash=DemandJson.requestHash(Map.of("demandId",demandId,"request",request)); Instant now=Instant.now();
        var command=repository.lockCommand(actor,org,"UPDATE",commandKey,hash,now);
        DemandDto replay=replay(command,hash,now); if(replay!=null) return replay;
        StoredDemand old=find(demandId,org);
        if(!old.state().equals("DRAFT") || old.deletedAt()!=null) throw new DemandFailure(409,"DEMAND_LOCKED","Nhu cầu hiện không cho phép chỉnh sửa.");
        if(old.version()!=expected) throw new DemandFailure(409,"VERSION_CONFLICT","Dữ liệu đã thay đổi, vui lòng tải lại.");
        if(!old.data().schoolOrgId().equals(input.schoolOrgId()) || !old.data().termId().equals(input.termId()))
            throw new DemandFailure(422,"INVALID_CRITERIA","Không được đổi trường hoặc kỳ của nhu cầu đã tạo.");
        references.validate(org,input); String criteriaHash=DemandJson.criteriaHash(input);
        if(old.version()==DemandValidator.MAX_VERSION || old.criteriaVersion()==DemandValidator.MAX_VERSION)
            throw new DemandFailure(409,"VERSION_CONFLICT","Phiên bản không thể tăng thêm.");
        var demand=new StoredDemand(demandId,org,"DRAFT",old.version()+1,
                old.criteriaVersion()+(old.criteriaHash().equals(criteriaHash)?0:1),criteriaHash,input,old.createdAt(),now,null);
        if(repository.update(demand,expected)!=1) throw new DemandFailure(409,"VERSION_CONFLICT","Dữ liệu đã thay đổi, vui lòng tải lại.");
        repository.addRevision(demand,snapshot(demand),actor,"UPDATE"); audit(actor,demand,"DEMAND_UPDATE");
        repository.completeCommand(actor,org,"UPDATE",commandKey,demand.dto()); return demand.dto();
    }
    @Transactional
    public Page<DemandDto> list(Authentication authentication,String organization,String termId,String state,boolean includeDeleted,int page,int size) {
        String org=DemandValidator.uuid(organization,"X-Organization-Id"); authorize(authentication,org);
        if(page<0 || page>10000 || size<1 || size>100) throw new DemandFailure(400,"INVALID_PAGINATION","Phân trang không hợp lệ.");
        String term=termId==null?null:DemandValidator.uuid(termId,"termId");
        if(state!=null && !state.equals("DRAFT") && !state.equals("READY")) throw new DemandFailure(400,"SCHEMA_VALIDATION","Trạng thái nhu cầu không hợp lệ.");
        return repository.list(org,term,state,includeDeleted,page,size);
    }
    @Override @Transactional
    public DemandCriteriaSnapshot read(Authentication authentication,String organization,String id,Long expectedCriteriaVersion,Purpose purpose) {
        String org=DemandValidator.uuid(organization,"X-Organization-Id"), demandId=DemandValidator.uuid(id,"demandId"); authorize(authentication,org);
        if(purpose!=Purpose.EVALUATE) throw new DemandFailure(403,"ACCESS_DENIED","Mục đích truy cập chưa được hỗ trợ.");
        if(expectedCriteriaVersion!=null) DemandValidator.version(expectedCriteriaVersion);
        StoredDemand demand=find(demandId,org);
        if(demand.deletedAt()!=null || !(demand.state().equals("DRAFT") || demand.state().equals("READY")))
            throw new DemandFailure(409,"DEMAND_LOCKED","Nhu cầu không thể dùng để đối chiếu.");
        if(expectedCriteriaVersion!=null && expectedCriteriaVersion.longValue()!=demand.criteriaVersion())
            throw new DemandFailure(409,"CRITERIA_STALE","Tiêu chí đã thay đổi, vui lòng lấy phiên bản mới.");
        references.validate(org,demand.data()); return repository.currentSnapshot(demand);
    }
    private String authorize(Authentication authentication,String org) {
        if(authentication==null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof AppPrincipal principal))
            throw new DemandFailure(401,"SESSION_EXPIRED","Phiên đăng nhập không hợp lệ.");
        var user=users.findByIdForUpdate(principal.userId()).orElseThrow(() -> new DemandFailure(401,"SESSION_EXPIRED","Phiên đăng nhập không hợp lệ."));
        if(user.status!=AccountStatus.ACTIVE || user.securityVersion!=principal.securityVersion())
            throw new DemandFailure(401,"SESSION_EXPIRED","Phiên đăng nhập đã hết hiệu lực.");
        if(!authorization.hasOrganization(authentication,org,Role.ENTERPRISE,"cooperation:enterprise:create"))
            throw new DemandFailure(403,"ACCESS_DENIED","Bạn không có quyền thao tác trong doanh nghiệp này.");
        if(!contractApproved) throw new DemandFailure(503,"CONTRACT_PENDING_SIGNOFF","Chức năng đang chờ xác nhận hợp đồng giữa các team.");
        return principal.userId();
    }
    private StoredDemand find(String id,String org) {
        return repository.findLocked(id,org).orElseThrow(() -> new DemandFailure(404,"RESOURCE_NOT_FOUND","Không tìm thấy nhu cầu trong phạm vi truy cập."));
    }
    private DemandDto replay(DemandRepository.Command command,String hash,Instant now) {
        if(!command.hash().equals(hash)) throw new DemandFailure(409,"IDEMPOTENCY_KEY_REUSED","Mã thao tác đã được dùng với dữ liệu khác.");
        if(command.response()==null) return null;
        if(!now.isBefore(command.createdAt().plus(24,ChronoUnit.HOURS))) throw new DemandFailure(409,"IDEMPOTENCY_RESPONSE_EXPIRED","Kết quả thao tác đã hết thời gian lưu; không thực hiện lại thao tác.");
        return DemandJson.read(command.response(),DemandDto.class);
    }
    private DemandCriteriaSnapshot snapshot(StoredDemand d) {
        return new DemandCriteriaSnapshot(d.id(),d.data().schoolOrgId(),d.enterpriseOrgId(),d.data().termId(),d.data().quantity(),d.version(),d.criteriaVersion(),"VALID",
                d.data().required(),d.data().preferred(),d.criteriaHash(),d.data().academicCatalogVersion(),d.data().taxonomyVersion(),null,null,d.updatedAt());
    }
    private void audit(String actor,StoredDemand demand,String reason) {
        try { audit.writeScoped(actor,"DEMAND_CHANGED","demand",demand.id(),"SUCCESS",demand.enterpriseOrgId(),reason); entityManager.flush(); }
        catch(RuntimeException failure) { throw new DemandFailure(500,"AUDIT_WRITE_FAILED","Không thể ghi nhật ký; thao tác đã được hủy."); }
    }
}
