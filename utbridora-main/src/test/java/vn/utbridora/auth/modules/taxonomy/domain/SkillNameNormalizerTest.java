/** DEV08 đã tạo kiểm thử đơn vị cho chuẩn hóa hoa thường, khoảng trắng và Unicode của tên kỹ năng. */
package vn.utbridora.auth.modules.taxonomy.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SkillNameNormalizerTest {
    @Test void caseAndRepeatedWhitespaceResolveToSameLookupKey() {
        assertEquals("java core", SkillNameNormalizer.normalize("  Java   Core "));
        assertEquals(SkillNameNormalizer.normalize(" JAVA Core "), SkillNameNormalizer.normalize("java   core"));
    }

    @Test void unicodeCompatibilityCharactersNormalizeConsistently() {
        assertEquals(SkillNameNormalizer.normalize("Java"), SkillNameNormalizer.normalize("Ｊａｖａ"));
    }

    @Test void nullIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SkillNameNormalizer.normalize(null));
    }
}
