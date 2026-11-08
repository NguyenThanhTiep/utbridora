package vn.utbridora.auth.modules.cooperation.domain;

import java.util.List;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.FieldError;

public final class DemandFailure extends RuntimeException {
    private final int status;
    private final String code;
    private final List<FieldError> fields;
    public DemandFailure(int status, String code, String message) {
        this(status, code, message, List.of());
    }
    public DemandFailure(int status, String code, String message, List<FieldError> fields) {
        super(message); this.status=status; this.code=code; this.fields=List.copyOf(fields);
    }
    public int status() { return status; }
    public String code() { return code; }
    public List<FieldError> fields() { return fields; }
    public static DemandFailure invalid(String field, String message) {
        return new DemandFailure(422, "INVALID_CRITERIA", "Dữ liệu nhu cầu chưa hợp lệ.",
                List.of(new FieldError(field, "INVALID_VALUE", message)));
    }
}
