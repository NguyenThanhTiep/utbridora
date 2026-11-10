
package vn.utbridora.auth.student;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.utbridora.auth.student.dto.EligibilityDto;
import vn.utbridora.auth.student.eligibility.StudentTermEligibility;
import vn.utbridora.auth.student.eligibility.StudentTermEligibilityRepository;

@Service
public class StudentEligibilityQueryService {

    private final StudentTermEligibilityRepository eligibilityRepository;
    private final StudentSchoolAccessGuard accessGuard;

    public StudentEligibilityQueryService(
            StudentTermEligibilityRepository eligibilityRepository,
            StudentSchoolAccessGuard accessGuard
    ) {
        this.eligibilityRepository = eligibilityRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<EligibilityDto> findBySchool(String schoolOrgId) {
        accessGuard.requireSchoolStaff(schoolOrgId);

        return eligibilityRepository.findAllForSchool(schoolOrgId)
                .stream()
                .filter(eligibility->accessGuard.canReadUser(eligibility.userId,schoolOrgId))
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public EligibilityDto findForSchool(
            String schoolOrgId,
            String studentRosterId,
            String termCode
    ) {
        accessGuard.requireSchoolStaff(schoolOrgId);

        StudentTermEligibility eligibility = eligibilityRepository
                .findForSchool(schoolOrgId, studentRosterId, termCode)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Eligibility not found"
                        )
                );

        if(!accessGuard.canReadUser(eligibility.userId,schoolOrgId)) throw new org.springframework.security.access.AccessDeniedException("Access denied");
        return toDto(eligibility);
    }

    private EligibilityDto toDto(
            StudentTermEligibility eligibility
    ) {
        return new EligibilityDto(
                eligibility.id,
                eligibility.studentRosterId,
                eligibility.termCode,
                eligibility.schoolApproved,
                eligibility.createdAt
        );
    }
}
