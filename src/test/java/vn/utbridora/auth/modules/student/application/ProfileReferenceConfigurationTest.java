package vn.utbridora.auth.modules.student.application;

import java.util.List;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.ProfileInput;
import static org.junit.jupiter.api.Assertions.*;

class ProfileReferenceConfigurationTest {
    ProfileInput input(String major) {
        return new ProfileInput(0,"44444444-4444-4444-8444-444444444444",major,false,List.of(),List.of(),
                false,List.of(),List.of(),List.of(),List.of(),List.of(),null,"0901234567");
    }
    @Test void unconfiguredCatalogAllowsAnUndeclaredProfileButCannotCertifyANewMajor() {
        var validator=new ProfileReferenceConfiguration().unavailableProfileReferences();
        assertDoesNotThrow(()->validator.validate("school",input(null)));
        var failure=assertThrows(StudentProfileException.class,()->validator.validate("school",input("SE")));
        assertEquals(503,failure.status);assertEquals("DEPENDENCY_UNAVAILABLE",failure.errorCode);
    }
}
