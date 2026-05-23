package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.BusinessRuleViolationException;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.dto.AddDependencyRequest;
import com.att.tdp.issueflow.ticket.dto.DependencyResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketDependencyService {

    private final TicketDependencyRepository ticketDependencyRepository;
    private final TicketRepository ticketRepository;
    private final AuditEventPublisher auditEventPublisher;

    public TicketDependencyService(
            TicketDependencyRepository ticketDependencyRepository,
            TicketRepository ticketRepository,
            AuditEventPublisher auditEventPublisher
    ) {
        this.ticketDependencyRepository = ticketDependencyRepository;
        this.ticketRepository = ticketRepository;
        this.auditEventPublisher = auditEventPublisher;
    }

    @Transactional
    public DependencyResponse addDependency(Long ticketId, AddDependencyRequest request) {
        return addDependency(ticketId, request, null);
    }

    @Transactional
    public DependencyResponse addDependency(Long ticketId, AddDependencyRequest request, CurrentUser currentUser) {
        Ticket ticket = findTicket(ticketId);
        Ticket blocker = findTicket(request.blockedBy());
        validateDependency(ticket, blocker);

        TicketDependency dependency = new TicketDependency();
        dependency.setTicket(ticket);
        dependency.setDependsOnTicket(blocker);
        dependency.setType(TicketDependencyType.BLOCKED_BY);

        DependencyResponse response = toResponse(ticketDependencyRepository.save(dependency));
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.ADD_DEPENDENCY,
                AuditableEntityType.TICKET_DEPENDENCY,
                ticket.getId(),
                null,
                response
        );
        return response;
    }

    @Transactional(readOnly = true)
    public List<DependencyResponse> getDependencies(Long ticketId) {
        if (!ticketRepository.existsActiveWithActiveProjectById(ticketId)) {
            throw new NotFoundException("Ticket not found");
        }
        return ticketDependencyRepository.findByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteDependency(Long ticketId, Long blockerId) {
        deleteDependency(ticketId, blockerId, null);
    }

    @Transactional
    public void deleteDependency(Long ticketId, Long blockerId, CurrentUser currentUser) {
        if (!ticketRepository.existsActiveWithActiveProjectById(ticketId)
                || !ticketRepository.existsActiveWithActiveProjectById(blockerId)) {
            throw new NotFoundException("Ticket not found");
        }
        if (!ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(ticketId, blockerId)) {
            throw new NotFoundException("Ticket not found");
        }
        ticketDependencyRepository.deleteByTicketIdAndDependsOnTicketId(ticketId, blockerId);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.REMOVE_DEPENDENCY,
                AuditableEntityType.TICKET_DEPENDENCY,
                ticketId,
                new RemovedDependencyAuditValue(ticketId, blockerId),
                null
        );
    }

    @Transactional(readOnly = true)
    public void validateNoUnresolvedBlockers(Long ticketId) {
        if (ticketDependencyRepository.existsUnresolvedBlocker(ticketId)) {
            throw new BusinessRuleViolationException("Ticket cannot move to DONE while blockers are unresolved");
        }
    }

    private void validateDependency(Ticket ticket, Ticket blocker) {
        if (ticket.getId().equals(blocker.getId())) {
            throw new BusinessRuleViolationException("Ticket cannot depend on itself");
        }
        if (!ticket.getProject().getId().equals(blocker.getProject().getId())) {
            throw new BusinessRuleViolationException("Dependent tickets must belong to the same project");
        }
        if (ticketDependencyRepository.existsByTicketIdAndDependsOnTicketId(ticket.getId(), blocker.getId())) {
            throw new ConflictException("Ticket dependency already exists");
        }
        if (createsCycle(ticket.getId(), blocker.getId(), new HashSet<>())) {
            throw new BusinessRuleViolationException("Ticket dependency cycle is not allowed");
        }
    }

    private boolean createsCycle(Long ticketId, Long currentBlockerId, Set<Long> visited) {
        if (currentBlockerId.equals(ticketId)) {
            return true;
        }
        if (!visited.add(currentBlockerId)) {
            return false;
        }
        return ticketDependencyRepository.findByTicketIdOrderByCreatedAtDesc(currentBlockerId).stream()
                .anyMatch(dependency -> createsCycle(ticketId, dependency.getDependsOnTicket().getId(), visited));
    }

    private Ticket findTicket(Long ticketId) {
        return ticketRepository.findActiveWithActiveProjectById(ticketId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    private DependencyResponse toResponse(TicketDependency dependency) {
        Ticket blocker = dependency.getDependsOnTicket();
        return new DependencyResponse(
                dependency.getTicket().getId(),
                blocker.getId(),
                blocker.getTitle(),
                blocker.getStatus()
        );
    }

    private record RemovedDependencyAuditValue(Long ticketId, Long blockedBy) {
    }
}
