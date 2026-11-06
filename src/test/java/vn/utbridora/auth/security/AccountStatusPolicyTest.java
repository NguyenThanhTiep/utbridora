package vn.utbridora.auth.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.utbridora.auth.user.*;

class AccountStatusPolicyTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "PENDING_ACTIVATION,PENDING_ACTIVATION,true", "PENDING_ACTIVATION,ACTIVE,false", "PENDING_ACTIVATION,SUSPENDED,true", "PENDING_ACTIVATION,DEACTIVATED,true",
        "ACTIVE,PENDING_ACTIVATION,false", "ACTIVE,ACTIVE,true", "ACTIVE,SUSPENDED,true", "ACTIVE,DEACTIVATED,true",
        "SUSPENDED,PENDING_ACTIVATION,false", "SUSPENDED,ACTIVE,true", "SUSPENDED,SUSPENDED,true", "SUSPENDED,DEACTIVATED,true",
        "DEACTIVATED,PENDING_ACTIVATION,false", "DEACTIVATED,ACTIVE,false", "DEACTIVATED,SUSPENDED,false", "DEACTIVATED,DEACTIVATED,true"
    })
    void allAdminTransitionsFollowSrs(AccountStatus from,AccountStatus to,boolean allowed) {
        assertEquals(allowed,AccountStatusPolicy.adminCanTransition(from,to));
    }
    @Test void suspensionAndReactivationAreAllowed(){
        assertTrue(AccountStatusPolicy.adminCanTransition(AccountStatus.ACTIVE,AccountStatus.SUSPENDED));
        assertTrue(AccountStatusPolicy.adminCanTransition(AccountStatus.SUSPENDED,AccountStatus.ACTIVE));
    }
    @Test void deactivationIsTerminalAndActivationNeedsInvitation(){
        assertFalse(AccountStatusPolicy.adminCanTransition(AccountStatus.DEACTIVATED,AccountStatus.ACTIVE));
        assertFalse(AccountStatusPolicy.adminCanTransition(AccountStatus.PENDING_ACTIVATION,AccountStatus.ACTIVE));
        assertTrue(AccountStatusPolicy.adminCanTransition(AccountStatus.ACTIVE,AccountStatus.DEACTIVATED));
    }
}
