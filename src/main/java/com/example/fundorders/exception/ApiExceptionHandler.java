package com.example.fundorders.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    public record ApiError(String code, String message) {
    }

    @ExceptionHandler({InvalidEventException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST", "Invalid message body"));
    }

    @ExceptionHandler(PublishFailedException.class)
    public ResponseEntity<ApiError> publishFailure(PublishFailedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError("KAFKA_UNAVAILABLE", "Message was not acknowledged by Kafka"));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> statusFailure(ResponseStatusException ex) {
        var code = switch (ex.getStatusCode().value()) {
            case 404 -> "NOT_FOUND";
            case 413 -> "PAYLOAD_TOO_LARGE";
            default -> "INVALID_REQUEST";
        };
        return ResponseEntity.status(ex.getStatusCode())
                .body(new ApiError(code, ex.getReason()));
    }
}
