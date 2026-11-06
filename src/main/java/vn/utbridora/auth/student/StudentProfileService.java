package vn.utbridora.auth.student;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudentProfileService {

    private final StudentProfileRepository profileRepository;

    public StudentProfileService(StudentProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getProfileByUserId(String userId) {
        StudentProfileEntity entity = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa tìm thấy hồ sơ sinh viên"));
        return mapToResponse(entity);
    }

    @Transactional
    public StudentProfileResponse createOrUpdateProfile(String userId, StudentProfileRequest request) {
        StudentProfileEntity entity = profileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    StudentProfileEntity newEntity = new StudentProfileEntity();
                    newEntity.setId(UUID.randomUUID().toString());
                    newEntity.setUserId(userId);
                    newEntity.setStatus(StudentProfileEntity.ProfileStatus.ACTIVE);
                    return newEntity;
                });

        if (!request.getStudentCode().equals(entity.getStudentCode()) &&
            profileRepository.existsByStudentCodeAndUniversityId(request.getStudentCode(), request.getUniversityId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mã số sinh viên đã tồn tại trong hệ thống.");
        }

        entity.setStudentCode(request.getStudentCode());
        entity.setUniversityId(request.getUniversityId());
        entity.setMajorCode(request.getMajorCode());
        entity.setAcademicTerm(request.getAcademicTerm());
        entity.setPhoneNumber(request.getPhoneNumber());
        entity.setBio(request.getBio());

        StudentProfileEntity saved = profileRepository.save(entity);
        return mapToResponse(saved);
    }

    private StudentProfileResponse mapToResponse(StudentProfileEntity entity) {
        return new StudentProfileResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getStudentCode(),
                entity.getUniversityId(),
                entity.getMajorCode(),
                entity.getAcademicTerm(),
                entity.getPhoneNumber(),
                entity.getBio(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}