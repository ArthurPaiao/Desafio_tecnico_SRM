package com.arthurpaiao.creditengine.api;

import com.arthurpaiao.creditengine.application.BusinessException;
import com.arthurpaiao.creditengine.application.ConditionsChangedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.sql.SQLException;
import java.util.List;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    public record FieldError(String field, String message) {}
    public record ErrorBody(String code, String message, List<FieldError> fieldErrors) {}

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> business(BusinessException error) {
        var status = switch (error.getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ALREADY_SETTLED, IDEMPOTENCY_CONFLICT, OPERATION_IN_PROGRESS -> HttpStatus.CONFLICT;
            case EXCHANGE_RATE_UNAVAILABLE, EXCHANGE_RATE_EXPIRED -> HttpStatus.UNPROCESSABLE_CONTENT;
        };
        return response(status, error.getCode().name(), error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Object> invalid(IllegalArgumentException error) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_INPUT", error.getMessage());
    }

    public record ConditionsError(String code, String message, List<FieldError> fieldErrors, ReceivableSimulation current) {}

    @ExceptionHandler(ConditionsChangedException.class)
    ResponseEntity<Object> changed(ConditionsChangedException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConditionsError("CONDITIONS_CHANGED",
                error.getMessage(), List.of(), error.getCurrent()));
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<Object> busy(PessimisticLockingFailureException error) {
        return response(HttpStatus.CONFLICT, "OPERATION_IN_PROGRESS", "Título em processamento; repita a mesma chave e pedido");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> integrity(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return response(HttpStatus.CONFLICT, "DUPLICATE", "Já existe um cadastro com essa identificação");
            }
        }
        return unexpected(error);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception error) {
        LOG.error("Falha interna na API", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Não foi possível concluir a operação");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException error,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(field -> new FieldError(field.getField(), field.getDefaultMessage())).toList();
        return new ResponseEntity<>(new ErrorBody("INVALID_INPUT", "Revise os campos informados", fields), headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception error, Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        return new ResponseEntity<>(new ErrorBody("HTTP_" + status.value(),
                "Requisição não aceita; confira formato, parâmetros, método e endereço", List.of()), headers, status);
    }

    private ResponseEntity<Object> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorBody(code, message, List.of()));
    }
}
