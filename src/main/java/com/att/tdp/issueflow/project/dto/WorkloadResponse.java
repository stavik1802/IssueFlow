package com.att.tdp.issueflow.project.dto;

import java.util.List;

public record WorkloadResponse(
        Long projectId,
        String projectName,
        List<MemberWorkload> members
) {

    public record MemberWorkload(
            Long userId,
            String username,
            long openTicketCount
    ) {
    }
}
