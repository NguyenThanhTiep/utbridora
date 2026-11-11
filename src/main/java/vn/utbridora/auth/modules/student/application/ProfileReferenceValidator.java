package vn.utbridora.auth.modules.student.application;

import vn.utbridora.auth.modules.student.api.StudentProfileDtos.ProfileInput;

/** Owner adapter for new academic/taxonomy references; implementations must read authoritative catalogs. */
public interface ProfileReferenceValidator {
    void validate(String schoolOrgId,ProfileInput input);
}
