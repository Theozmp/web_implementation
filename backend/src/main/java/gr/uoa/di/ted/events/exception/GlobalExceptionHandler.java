package gr.uoa.di.ted.events.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps exceptions to HTTP responses in one place. Every handler returns the same
 * JSON envelope { "code", "message", "timestamp" }, with validation errors adding a
 * "fieldErrors" map. Keeping this out of the controllers keeps them thin and the
 * error contract easy to document in Swagger.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // domain exceptions (AppException hierarchy)

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleApp(AppException ex) {
        return body(ex.getStatus(), ex.getCode(), ex.getMessage(), null);
    }

    // bean validation failures (@Valid on @RequestBody)

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        // collect field errors, merging multiple messages per field into a list
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(
                                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                                Collectors.toList()
                        )
                ));

        // class-level constraint violations, e.g. @PasswordMatch
        ex.getBindingResult().getGlobalErrors()
                .forEach(ge -> fieldErrors
                        .computeIfAbsent("_global", k -> new java.util.ArrayList<>())
                        .add(ge.getDefaultMessage()));

        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", fieldErrors);
    }

    // catch-all

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", null);
    }

    // helper

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String code,
                                                     String message, Object fieldErrors) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code",      code);
        map.put("message",   message);
        map.put("timestamp", Instant.now().toString());
        if (fieldErrors != null) map.put("fieldErrors", fieldErrors);
        return ResponseEntity.status(status).body(map);
    }
}
