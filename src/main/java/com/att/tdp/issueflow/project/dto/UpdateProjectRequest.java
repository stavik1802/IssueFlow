package com.att.tdp.issueflow.project.dto;

import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 160)
        String name,

        @Size(max = 2000)
        String description
) {
}
