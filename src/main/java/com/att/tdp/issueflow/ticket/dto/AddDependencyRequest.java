package com.att.tdp.issueflow.ticket.dto;

import jakarta.validation.constraints.NotNull;

public record AddDependencyRequest(
        @NotNull Long blockedBy
) {
}
