package vn.utbridora.auth.operations;

/** Safe C.0 failure, with no payload or SQL details in the client message. */
public final class OperationFailure extends RuntimeException {
    public final int status;
    public final String code;
    public final Integer retryAfterSeconds;
    public final java.util.List<Object> fieldErrors;
    public OperationFailure(int status, String code, String message, Integer retryAfterSeconds) {
        this(status,code,message,retryAfterSeconds,java.util.List.of());
    }
    public OperationFailure(int status,String code,String message,Integer retryAfterSeconds,java.util.List<?> fields) {
        super(message);this.status=status;this.code=code;this.retryAfterSeconds=retryAfterSeconds;this.fieldErrors=java.util.List.copyOf(fields);
    }
}
