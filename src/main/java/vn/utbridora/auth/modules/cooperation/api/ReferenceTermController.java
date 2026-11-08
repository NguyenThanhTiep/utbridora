package vn.utbridora.auth.modules.cooperation.api;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.authorization.AuthorizationPolicy;
import vn.utbridora.auth.modules.cooperation.domain.Term;
import vn.utbridora.auth.modules.cooperation.persistence.TermRepository;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.student.port.TermReferencePort.TermReference;
import vn.utbridora.auth.student.port.TermReferencePort.Status;
import vn.utbridora.auth.user.Role;

/** BC21 read-only TermReference. School filters never grant access by themselves. */
@RestController
@RequestMapping("/api/v1/terms")
public class ReferenceTermController {
    private final TermRepository terms;
    private final AuthorizationPolicy access;
    public ReferenceTermController(TermRepository terms,AuthorizationPolicy access) { this.terms=terms;this.access=access; }
    public record Page(List<TermReference> content,int page,int size,long totalElements,int totalPages) {}
    @GetMapping
    public Page list(Authentication actor,@RequestParam String schoolOrgId,
                     @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        requireSchool(actor,schoolOrgId);
        if (page<0 || page>10000 || size<1 || size>100) throw new OperationFailure(400,"INVALID_PAGINATION","Phân trang không hợp lệ.",null);
        var result=terms.findBySchoolOrgId(schoolOrgId,PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,"createdAt").and(Sort.by("id"))));
        return new Page(result.stream().map(ReferenceTermController::view).toList(),page,size,result.getTotalElements(),result.getTotalPages());
    }
    @GetMapping("/{termId}")
    public TermReference get(Authentication actor,@PathVariable String termId) {
        Term term=terms.findById(termId).orElseThrow(ReferenceTermController::missing);
        requireSchool(actor,term.schoolOrgId);return view(term);
    }
    private void requireSchool(Authentication actor,String school) {
        if (!access.hasOrganization(actor,school,Role.STUDENT,"student_profile:self:read")
                && !access.hasOrganization(actor,school,Role.SCHOOL_STAFF,"placement:school:write")) throw missing();
    }
    private static TermReference view(Term term) {
        return new TermReference(term.id,term.termCode,term.schoolOrgId,term.startDate,term.endDate,Status.valueOf(term.status.name()),term.version);
    }
    private static OperationFailure missing() { return new OperationFailure(404,"RESOURCE_NOT_FOUND","Không tìm thấy kỳ học trong phạm vi truy cập.",null); }
}
