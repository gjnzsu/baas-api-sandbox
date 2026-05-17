package com.example.baas.sandbox.api;

import com.example.baas.sandbox.payment.PaymentException;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class RestExceptionHandler {

    @ExceptionHandler(PaymentException.class)
    ResponseEntity<ErrorEnvelope> handlePaymentException(PaymentException exception) {
        return ResponseEntity.status(exception.status())
                .body(ErrorEnvelope.of(exception.code(), exception.getMessage(), exception.details()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, Object> details = Map.of("errors", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", error.getDefaultMessage()))
                .toList());
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorEnvelope> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ErrorEnvelope.of("VALIDATION_ERROR", "Request validation failed", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorEnvelope> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorEnvelope.of("INTERNAL_ERROR", "Unexpected sandbox error", Map.of()));
    }
}
