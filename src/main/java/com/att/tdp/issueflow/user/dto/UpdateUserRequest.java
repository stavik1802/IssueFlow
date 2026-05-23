package com.att.tdp.issueflow.user.dto;

import com.att.tdp.issueflow.user.Role;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank
        @Size(max = 160)
        @JsonProperty("fullName")
        @JsonAlias("full_name")
        String fullName,

        @NotNull
        Role role
) {
}
