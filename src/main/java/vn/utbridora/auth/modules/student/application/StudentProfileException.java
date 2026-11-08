package vn.utbridora.auth.modules.student.application;

import java.util.List;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.FieldErrorView;

/** Domain failure mapped to the C.0 envelope by StudentProfileErrors; message is safe for end users. */
public class StudentProfileException extends RuntimeException {
    public final int status;
    public final String errorCode;
    public final List<FieldErrorView> fieldErrors;
    public StudentProfileException(int status,String errorCode,String message,List<FieldErrorView> fieldErrors) {
        super(message); this.status=status; this.errorCode=errorCode; this.fieldErrors=List.copyOf(fieldErrors);
    }
    static StudentProfileException of(int status,String errorCode,String message) {
        return new StudentProfileException(status,errorCode,message,List.of());
    }
}
