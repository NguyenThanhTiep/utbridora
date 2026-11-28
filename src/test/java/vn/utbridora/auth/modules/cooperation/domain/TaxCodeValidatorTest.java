package vn.utbridora.auth.modules.cooperation.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidationResult.CodeKind.*;

class TaxCodeValidatorTest {
    @Test void realEnterpriseCodesAndBranches() {
        for (String code : new String[]{"0101248141", "0300588569", "0100109106", "0100112437"}) {
            assertTrue(TaxCodeValidator.validate(code).validEnterprise(), code);
            assertTrue(TaxCodeValidator.validate(code + "-001").validEnterprise());
            assertTrue(TaxCodeValidator.validate(code + "-999").validEnterprise());
            assertFalse(TaxCodeValidator.validate(code + "-000").validEnterprise());
        }
        assertEquals("0101248141-001", TaxCodeValidator.normalize(" 0101 248141 – 001 "));
        assertEquals(BRANCH_13, TaxCodeValidator.validate("0101248141001").codeKind());
    }
    @Test void invalidChecksumRemainderZeroAndPersonalIdentifiers() {
        assertFalse(TaxCodeValidator.validate("0101248142").checksumValid());
        // 1*31 + 1*13 = 44; S % 11 == 0, decimal check digit is 0.
        assertEquals(0, (31 + 13) % 11);
        assertTrue(TaxCodeValidator.validate("1000010000").validEnterprise());
        var personal = TaxCodeValidator.validate("079123456789");
        assertEquals(PERSONAL_ID_12, personal.codeKind()); assertNull(personal.checksumValid()); assertFalse(personal.validEnterprise());
        for (String code : new String[]{"CT-TEST", "SYNTHETIC", "DEMO-123", "123", "0101248141/001", "０１０１２４８１４１", ""})
            assertEquals(INVALID_FORMAT, TaxCodeValidator.validate(code).codeKind());
        assertEquals(INVALID_FORMAT, TaxCodeValidator.validate(null).codeKind());
    }
}
