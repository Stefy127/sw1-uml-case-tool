package com.sw1.umltool.common.exception;

import com.sw1.umltool.features.diagram.operation.OperationApplicationException;
import com.sw1.umltool.features.diagram.operation.VersionConflictException;
import com.sw1.umltool.features.diagram.service.DiagramNotFoundException;
import com.sw1.umltool.features.diagram.service.DiagramSerializationException;
import com.sw1.umltool.features.ai.voice.service.AiVoiceTimeoutException;
import com.sw1.umltool.features.importexport.service.XmiExportException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.sw1.umltool.features.project.exception.ProjectForbiddenException;
import com.sw1.umltool.features.project.exception.ProjectNotFoundException;
import com.sw1.umltool.features.ai.chat.exception.AiConversationNotFoundException;
import com.sw1.umltool.features.ai.memory.exception.AiMemoryNotFoundException;
import com.sw1.umltool.features.ai.analysis.exception.AiAnalysisException;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectNotFound(ProjectNotFoundException exception) { return error(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", exception.getMessage()); }

    @ExceptionHandler(ProjectForbiddenException.class)
    public ResponseEntity<ApiErrorResponse> handleProjectForbidden(ProjectForbiddenException exception) { return error(HttpStatus.FORBIDDEN, "PROJECT_FORBIDDEN", exception.getMessage()); }

    @ExceptionHandler(DiagramNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleDiagramNotFound(DiagramNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "DIAGRAM_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AiConversationNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAiConversationNotFound(AiConversationNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "AI_CONVERSATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AiMemoryNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAiMemoryNotFound(AiMemoryNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, "AI_MEMORY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(AiAnalysisException.class)
    public ResponseEntity<ApiErrorResponse> handleAiAnalysis(AiAnalysisException exception) {
        return error(HttpStatus.BAD_GATEWAY, "AI_ANALYSIS_ERROR", exception.getMessage());
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

    @ExceptionHandler(XmiExportException.class)
    public ResponseEntity<ApiErrorResponse> handleXmiExport(XmiExportException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "XMI_EXPORT_ERROR", exception.getMessage());
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

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleUnavailable(IllegalStateException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    @ExceptionHandler(AiVoiceTimeoutException.class)
    public ResponseEntity<ApiErrorResponse> handleAiTimeout(AiVoiceTimeoutException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "AI_SERVICE_UNAVAILABLE", exception.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.builder()
                .code(code)
                .message(message == null ? "Request could not be processed" : message)
                .timestamp(java.time.LocalDateTime.now())
                .build());
    }
}
