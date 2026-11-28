package vn.utbridora.auth.modules.cooperation.integration;

import org.junit.jupiter.api.Test;
import vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidator;
import vn.utbridora.auth.modules.cooperation.port.TaxAuthorityLookupPort.*;
import static org.junit.jupiter.api.Assertions.*;

class MockTaxAuthorityAdapterTest {
    final MockTaxAuthorityAdapter adapter = new MockTaxAuthorityAdapter();
    @Test void deterministicSamplesAndBranchAreClearlyMarkedMock() {
        for (String code : new String[]{"0101248141", "0300588569", "0100109106", "0100112437", "0101248141-001"}) {
            var found = assertInstanceOf(Found.class, adapter.lookup(code));
            assertEquals(code, found.taxCode()); assertEquals(TaxpayerStatus.ACTIVE, found.status()); assertEquals("mock", found.provider());
        }
    }
    @Test void validChecksumSyntheticInactiveExamplesAndUnknownCodes() {
        assertEquals(10, (3 * 29) % 11);
        assertTrue(TaxCodeValidator.validate("0300000000").validEnterprise());
        assertTrue(TaxCodeValidator.validate("0300000018").validEnterprise());
        assertFalse(TaxCodeValidator.validate("0300000001").validEnterprise());
        assertEquals(TaxpayerStatus.SUSPENDED, ((Found)adapter.lookup("0300000000")).status());
        assertEquals(TaxpayerStatus.DISSOLVED, ((Found)adapter.lookup("0300000018")).status());
        for (String code : new String[]{"1000010000", "CT-TEST", "SYNTHETIC", "0101248141-002"}) assertInstanceOf(NotFound.class, adapter.lookup(code));
    }
}
