package vn.utbridora.auth.modules.cooperation.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.cooperation.domain.VietnameseBusinessTextNormalizer.*;

class VietnameseBusinessTextNormalizerTest {
    @Test void canonicalizesLegalNamesAndVietnameseDiacritics() {
        assertEquals(MatchKind.NORMALIZED_MATCH, companyName("Công ty TNHH MTV TM DV SX XD Đông Đô",
            "CONG TY TRACH NHIEM HUU HAN MOT THANH VIEN THUONG MAI DICH VU SAN XUAT XAY DUNG DONG DO").kind());
        assertTrue(companyName("Công ty CP FPT", "CÔNG TY CỔ PHẦN FPT").matches());
        assertEquals(MatchKind.EXACT, companyName("FPT", "FPT").kind());
    }
    @Test void canonicalizesAddressesWithoutAcceptingDifferentHouseNumbers() {
        for (String city : new String[]{"TP HCM", "TPHCM", "TP.HCM", "TP HO CHI MINH"})
            assertTrue(address("123 Đường A, P.1, Q.3, " + city, "123 DUONG A PHUONG 1 QUAN 3 HO CHI MINH").matches());
        assertTrue(address("TX. Sơn Tây, TP HN", "THI XA SON TAY HA NOI").matches());
        assertTrue(address("TT. Đông Anh, HN", "THI TRAN DONG ANH HA NOI").matches());
        assertFalse(address("123 Đường A Q1", "456 Đường B Q2").matches());
        assertFalse(address("123 Đường A, Q.1, TP.HCM", "124 Đường A, Q.1, TP.HCM").matches());
        assertFalse(companyName("Công ty ABC", "Công ty XYZ").matches());
        assertFalse(companyName("Công ty TNHH MTV Thương mại A", "Công ty TNHH MTV Thương mại B").matches());
        assertFalse(companyName("", "").matches());
    }
}
