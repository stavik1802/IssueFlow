package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.dto.WorkloadResponse;
import com.att.tdp.issueflow.project.dto.WorkloadResponse.MemberWorkload;
import com.att.tdp.issueflow.ticket.TicketRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkloadService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TicketRepository ticketRepository;

    public WorkloadService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            TicketRepository ticketRepository
    ) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public WorkloadResponse getWorkload(Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        Map<Long, Long> openTicketCounts = openTicketCounts(projectId);

        return new WorkloadResponse(
                project.getId(),
                project.getName(),
                projectMemberRepository.findByProjectId(projectId).stream()
                        .map(member -> new MemberWorkload(
                                member.getUser().getId(),
                                member.getUser().getUsername(),
                                openTicketCounts.getOrDefault(member.getUser().getId(), 0L)
                        ))
                        .sorted(java.util.Comparator
                                .comparingLong(MemberWorkload::openTicketCount)
                                .thenComparing(MemberWorkload::username, String.CASE_INSENSITIVE_ORDER))
                        .toList()
        );
    }

    private Map<Long, Long> openTicketCounts(Long projectId) {
        return ticketRepository.countOpenTicketsByAssignee(projectId).stream()
                .collect(Collectors.toMap(
                        TicketRepository.AssigneeOpenTicketCount::getUserId,
                        TicketRepository.AssigneeOpenTicketCount::getOpenTicketCount,
                        Long::sum
                ));
    }
}
