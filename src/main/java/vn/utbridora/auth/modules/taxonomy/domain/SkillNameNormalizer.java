/** DEV08 đã tạo bộ chuẩn hóa tên kỹ năng để tra cứu không phân biệt hoa thường và gộp khoảng trắng. */
package vn.utbridora.auth.modules.taxonomy.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Chuẩn hóa tên kỹ năng cho UT-T4-05: không phân biệt hoa thường, gộp khoảng trắng và chuẩn Unicode.
 * CONTRACT_PENDING_SIGNOFF (chưa xác nhận hợp đồng): thuật toán NFKC (chuẩn hóa tương thích Unicode)
 * và Locale.ROOT (quy tắc chữ thường độc lập ngôn ngữ) theo BC12; cần T2/T5 xác nhận.
 */
public final class SkillNameNormalizer {
    private SkillNameNormalizer() {}

    public static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("Skill name is required");
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replaceAll("\\s+", " ")
            .toLowerCase(Locale.ROOT);
    }
}
