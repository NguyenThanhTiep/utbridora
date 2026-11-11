package vn.utbridora.auth.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String,String>> invalid(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST", "message", e.getMessage()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String,String>> invalidBody(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "INVALID_REQUEST", "message", "Please check request fields"));
    }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String,String>> conflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "CONFLICT", "message", "Operation conflicts with the current state"));
    }
    @ExceptionHandler({org.springframework.dao.DataIntegrityViolationException.class,
        org.springframework.dao.ConcurrencyFailureException.class})
    ResponseEntity<Map<String,String>> databaseConflict(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error","CONFLICT","message","Concurrent change or duplicate data; reload and try again"));
    }
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<Map<String,String>> malformed(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error","INVALID_REQUEST","message","Please check request fields"));
    }
    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<Map<String,String>> authentication(Exception e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error","SESSION_EXPIRED","message","Session expired or revoked"));
    }
}
