package vn.utbridora.auth.modules.cooperation.domain;

import static vn.utbridora.auth.modules.cooperation.domain.TaxCodeValidationResult.CodeKind.*;

/** Structural/checksum check only; it cannot prove registration or current tax status. */
public final class TaxCodeValidator {
    private static final int[] WEIGHTS = {31, 29, 23, 19, 17, 13, 7, 5, 3};
    private TaxCodeValidator() {}
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.strip().replaceAll("[\\s\\p{Z}]+", "").replaceAll("[‐‑‒–—−]", "-");
        return value.matches("[0-9]{13}") ? value.substring(0, 10) + "-" + value.substring(10) : value;
    }
    public static TaxCodeValidationResult validate(String raw) {
        String code = normalize(raw);
        if (code.matches("[0-9]{12}")) return new TaxCodeValidationResult(code, PERSONAL_ID_12, null,
            "Số định danh cá nhân 12 chữ số; cần đối chiếu hồ sơ, không kiểm checksum MST doanh nghiệp.");
        if (code.matches("[0-9]{10}(-[0-9]{3})?")) {
            if (code.endsWith("-000")) return new TaxCodeValidationResult(code, INVALID_FORMAT, false, "Mã đơn vị phụ thuộc phải từ 001 đến 999.");
            int sum = 0;
            for (int i = 0; i < WEIGHTS.length; i++) sum += (code.charAt(i) - '0') * WEIGHTS[i];
            // Remainder 0 yields 10: retain its decimal unit (0), identically on the client.
            boolean valid = (10 - sum % 11) % 10 == code.charAt(9) - '0';
            return new TaxCodeValidationResult(code, code.length() == 10 ? ENTERPRISE_10 : BRANCH_13, valid,
                valid ? "MST đúng cấu trúc và checksum; chưa xác nhận tình trạng hoạt động." : "MST không đúng checksum. Vui lòng kiểm tra lại mã.");
        }
        return new TaxCodeValidationResult(code, INVALID_FORMAT, null, code.isEmpty() ? "Nhập mã số thuế để tra cứu."
            : "Chưa nhận diện MST: dùng 10 số, 10 số-3 số chi nhánh hoặc số định danh 12 số. Vẫn có thể lưu hồ sơ để đối chiếu thủ công.");
    }
}
