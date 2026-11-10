package vn.utbridora.auth.operations;

/** Safe C.0 failure, with no payload or SQL details in the client message. */
public final class OperationFailure extends RuntimeException {
    public final int status;
    public final String code;
    public final Integer retryAfterSeconds;
    public OperationFailure(int status, String code, String message, Integer retryAfterSeconds) {
        super(message); this.status=status; this.code=code; this.retryAfterSeconds=retryAfterSeconds;
    }
}
