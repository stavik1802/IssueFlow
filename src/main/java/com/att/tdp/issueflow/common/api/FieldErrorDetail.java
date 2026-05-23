package com.att.tdp.issueflow.common.api;

public record FieldErrorDetail(
        String field,
        String message,
        Object rejectedValue
) {
}
