package com.att.tdp.issueflow.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.common.api.ApiError;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.MethodParameter;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-05-19T10:15:30Z");

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void handlesMethodArgumentValidationErrors() throws NoSuchMethodException {
        TestRequest target = new TestRequest();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "name", "", false, null, null, "must not be blank"));

        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("validate", TestRequest.class),
                0
        );
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ApiError> response = handler.handleMethodArgumentNotValid(exception, request("/projects"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().timestamp()).isEqualTo(NOW);
        assertThat(response.getBody().message()).isEqualTo("Request validation failed");
        assertThat(response.getBody().path()).isEqualTo("/projects");
        assertThat(response.getBody().fieldErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.field()).isEqualTo("name");
                    assertThat(error.message()).isEqualTo("must not be blank");
                    assertThat(error.rejectedValue()).isEqualTo("");
                });
    }

    @Test
    void handlesConstraintViolationErrors() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("create.projectKey");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must match pattern");
        when(violation.getInvalidValue()).thenReturn("bad key");

        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiError> response = handler.handleConstraintViolation(exception, request("/projects"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fieldErrors())
                .singleElement()
                .satisfies(error -> {
                    assertThat(error.field()).isEqualTo("create.projectKey");
                    assertThat(error.message()).isEqualTo("must match pattern");
                    assertThat(error.rejectedValue()).isEqualTo("bad key");
                });
    }

    @Test
    void mapsCustomExceptionsToExpectedStatusCodes() {
        assertStatus(handler.handleNotFound(new NotFoundException("missing"), request("/x")), HttpStatus.NOT_FOUND);
        assertStatus(handler.handleConflict(new ConflictException("conflict"), request("/x")), HttpStatus.CONFLICT);
        assertStatus(handler.handleForbidden(new ForbiddenException("forbidden"), request("/x")), HttpStatus.FORBIDDEN);
        assertStatus(handler.handleBadRequest(new BadRequestException("bad"), request("/x")), HttpStatus.BAD_REQUEST);
        assertStatus(
                handler.handleOptimisticLockingFailure(
                        new OptimisticLockingFailureException("stale"),
                        request("/x")
                ),
                HttpStatus.CONFLICT
        );
        ResponseEntity<ApiError> lockResponse = handler.handleLockedResource(
                new CannotAcquireLockException("locked"),
                request("/tickets/1")
        );
        assertStatus(lockResponse, HttpStatus.CONFLICT);
        assertThat(lockResponse.getBody().message()).isEqualTo("Resource is currently being updated");
        assertStatus(
                handler.handleBusinessRuleViolation(new BusinessRuleViolationException("invalid transition"), request("/x")),
                HttpStatus.UNPROCESSABLE_ENTITY
        );
        assertStatus(
                handler.handleDataIntegrityViolation(new DataIntegrityViolationException("duplicate"), request("/x")),
                HttpStatus.CONFLICT
        );
    }

    @Test
    void mapsFrameworkInputErrorsToInformativeStatusCodes() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                getClass().getDeclaredMethod("validate", TestRequest.class),
                0
        );

        assertStatus(
                handler.handleMethodArgumentTypeMismatch(
                        new MethodArgumentTypeMismatchException("abc", Long.class, "id", parameter, null),
                        request("/tickets/abc")
                ),
                HttpStatus.BAD_REQUEST
        );
        assertStatus(
                handler.handleMissingServletRequestParameter(
                        new MissingServletRequestParameterException("projectId", "Long"),
                        request("/tickets/export")
                ),
                HttpStatus.BAD_REQUEST
        );
        assertStatus(
                handler.handleMissingServletRequestPart(
                        new MissingServletRequestPartException("file"),
                        request("/tickets/1/attachments")
                ),
                HttpStatus.BAD_REQUEST
        );
        assertStatus(
                handler.handleMaxUploadSizeExceeded(
                        new MaxUploadSizeExceededException(10),
                        request("/tickets/1/attachments")
                ),
                HttpStatus.PAYLOAD_TOO_LARGE
        );
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        ResponseEntity<ApiError> response = handler.handleUnexpected(
                new IllegalStateException("internal failure detail"),
                request("/tickets")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().path()).isEqualTo("/tickets");
    }

    private static MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    private static void assertStatus(ResponseEntity<ApiError> response, HttpStatus status) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(status.value());
    }

    @SuppressWarnings("unused")
    private void validate(TestRequest request) {
    }

    private static final class TestRequest {
    }
}
