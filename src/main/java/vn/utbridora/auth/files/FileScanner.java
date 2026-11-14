package vn.utbridora.auth.files;

import java.io.InputStream;
import java.time.Duration;

/** Trusted scanner port; an error or an unrecognized response can never mean CLEAN. */
public interface FileScanner {
    enum Outcome { CLEAN, REJECTED, RETRY }
    enum Reason { CLEAN, MALWARE_DETECTED, SCANNER_UNAVAILABLE, SCANNER_TIMEOUT, SCANNER_PROTOCOL_ERROR, FILE_INVALID, FILE_INTEGRITY_ERROR }
    record Result(Outcome outcome,Reason reason) {
        public Result {
            if(outcome==null || reason==null || (outcome==Outcome.CLEAN)!=(reason==Reason.CLEAN))
                throw new IllegalArgumentException("Invalid scan verdict");
        }
        public static Result clean() {return new Result(Outcome.CLEAN,Reason.CLEAN);}
        public static Result retry(Reason reason) {return new Result(Outcome.RETRY,reason);}
        public static Result reject(Reason reason) {return new Result(Outcome.REJECTED,reason);}
    }
    Result scan(InputStream input,long size,Duration remaining);
}
