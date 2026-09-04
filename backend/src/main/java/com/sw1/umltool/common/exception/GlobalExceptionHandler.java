package com.sw1.umltool.common.exception;

import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.DiagramSerializationException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DiagramNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDiagramNotFound(DiagramNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DIAGRAM_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(VersionConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleVersionConflict(VersionConflictException exception) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(OperationApplicationException.class)
    public ResponseEntity<ApiErrorResponse> handleOperationApplication(OperationApplicationException exception) {
        return error(HttpStatus.BAD_REQUEST, "OPERATION_APPLICATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(DiagramSerializationException.class)
    public ResponseEntity<ApiErrorResponse> handleSerialization(DiagramSerializationException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "DIAGRAM_SERIALIZATION_ERROR", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(RuntimeException exception) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
                .code(code)
                .message(message == null ? "Request could not be processed" : message)
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }
}
