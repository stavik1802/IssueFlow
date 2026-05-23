package com.att.tdp.issueflow.common.exception;

import com.att.tdp.issueflow.common.api.ApiError;
import com.att.tdp.issueflow.common.api.FieldErrorDetail;
import com.att.tdp.issueflow.ticket.TicketPriority;
import com.att.tdp.issueflow.ticket.TicketStatus;
import com.att.tdp.issueflow.ticket.TicketType;
import com.att.tdp.issueflow.user.Role;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField))
                .map(error -> new FieldErrorDetail(
                        error.getField(),
                        error.getDefaultMessage(),
                        error.getRejectedValue()
                ))
                .toList();

        ApiError apiError = ApiError.withFieldErrors(
                now(),
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorDetail> fieldErrors = exception.getConstraintViolations().stream()
                .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                .map(this::toFieldErrorDetail)
                .toList();

        ApiError apiError = ApiError.withFieldErrors(
                now(),
                HttpStatus.BAD_REQUEST,
                "Request validation failed",
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.badRequest().body(apiError);
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> handleNotFound(NotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> handleConflict(ConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.getMessage(), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "Request conflicts with existing persisted data", request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLockingFailure(
            OptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.CONFLICT, "Resource update conflict", request);
    }

    @ExceptionHandler({
            CannotAcquireLockException.class,
            LockTimeoutException.class,
            PessimisticLockException.class,
            org.hibernate.PessimisticLockException.class,
            org.hibernate.exception.LockAcquisitionException.class,
            PessimisticLockingFailureException.class
    })
    ResponseEntity<ApiError> handleLockedResource(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "Resource is currently being updated", request);
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<ApiError> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return error(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> handleBadRequest(BadRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        String name = exception.getName();
        return error(HttpStatus.BAD_REQUEST, "Invalid value for parameter: " + name, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ApiError> handleMissingServletRequestParameter(
            MissingServletRequestParameterException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter: " + exception.getParameterName(), request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ApiError> handleMissingServletRequestPart(
            MissingServletRequestPartException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.BAD_REQUEST, "Missing required multipart part: " + exception.getRequestPartName(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the configured size limit", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        if (exception.getMostSpecificCause() instanceof InvalidFormatException invalidFormatException
                && invalidFormatException.getTargetType() == Role.class) {
            return error(HttpStatus.BAD_REQUEST, "role must be one of: ADMIN, DEVELOPER", request);
        }
        if (exception.getMostSpecificCause() instanceof InvalidFormatException invalidFormatException) {
            Class<?> targetType = invalidFormatException.getTargetType();
            if (targetType == TicketStatus.class) {
                return error(HttpStatus.BAD_REQUEST,
                        "Status must be one of: TODO, IN_PROGRESS, IN_REVIEW, DONE.", request);
            }
            if (targetType == TicketPriority.class) {
                return error(HttpStatus.BAD_REQUEST,
                        "Priority must be one of: LOW, MEDIUM, HIGH, CRITICAL.", request);
            }
            if (targetType == TicketType.class) {
                return error(HttpStatus.BAD_REQUEST,
                        "Type must be one of: BUG, FEATURE, TECHNICAL.", request);
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Request body is malformed or contains invalid values", request);
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    ResponseEntity<ApiError> handleBusinessRuleViolation(
            BusinessRuleViolationException exception,
            HttpServletRequest request
    ) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled request exception", exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity
                .status(status)
                .body(ApiError.of(now(), status, message, request.getRequestURI()));
    }

    private FieldErrorDetail toFieldErrorDetail(ConstraintViolation<?> violation) {
        return new FieldErrorDetail(
                violation.getPropertyPath().toString(),
                violation.getMessage(),
                violation.getInvalidValue()
        );
    }

    private Instant now() {
        return Instant.now(clock);
    }
}
