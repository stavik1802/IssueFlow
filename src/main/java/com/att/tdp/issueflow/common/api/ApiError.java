package com.att.tdp.issueflow.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDetail> fieldErrors
) {

    public ApiError {
        fieldErrors = fieldErrors == null || fieldErrors.isEmpty() ? null : List.copyOf(fieldErrors);
    }

    public static ApiError of(Instant timestamp, HttpStatus status, String message, String path) {
        return new ApiError(timestamp, status.value(), status.getReasonPhrase(), message, path, null);
    }

    public static ApiError withFieldErrors(
            Instant timestamp,
            HttpStatus status,
            String message,
            String path,
            List<FieldErrorDetail> fieldErrors
    ) {
        return new ApiError(timestamp, status.value(), status.getReasonPhrase(), message, path, fieldErrors);
    }
}
