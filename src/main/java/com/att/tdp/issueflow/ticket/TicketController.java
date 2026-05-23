package com.att.tdp.issueflow.ticket;

import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.ticket.dto.AddDependencyRequest;
import com.att.tdp.issueflow.ticket.dto.CreateTicketRequest;
import com.att.tdp.issueflow.ticket.dto.DeletedTicketResponse;
import com.att.tdp.issueflow.ticket.dto.DependencyResponse;
import com.att.tdp.issueflow.ticket.dto.TicketResponse;
import com.att.tdp.issueflow.ticket.dto.UpdateTicketRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tickets")
public class TicketController {

    private final TicketService ticketService;
    private final TicketDependencyService ticketDependencyService;

    public TicketController(TicketService ticketService, TicketDependencyService ticketDependencyService) {
        this.ticketService = ticketService;
        this.ticketDependencyService = ticketDependencyService;
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        TicketResponse response = ticketService.create(request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public TicketResponse getById(@PathVariable Long id) {
        return ticketService.getById(id);
    }

    @GetMapping
    public List<TicketResponse> getByProject(@RequestParam Long projectId) {
        return ticketService.getByProject(projectId);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTicketRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ticketService.update(id, request, currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ticketService.delete(id, currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{ticketId}/dependencies")
    public ResponseEntity<Void> addDependency(
            @PathVariable Long ticketId,
            @Valid @RequestBody AddDependencyRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ticketDependencyService.addDependency(ticketId, request, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{ticketId}/dependencies")
    public List<DependencyResponse> getDependencies(@PathVariable Long ticketId) {
        return ticketDependencyService.getDependencies(ticketId);
    }

    @DeleteMapping("/{ticketId}/dependencies/{blockerId}")
    public ResponseEntity<Void> deleteDependency(
            @PathVariable Long ticketId,
            @PathVariable Long blockerId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ticketDependencyService.deleteDependency(ticketId, blockerId, currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        ticketService.restore(id, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/deleted")
    public List<DeletedTicketResponse> getDeletedByProject(
            @RequestParam Long projectId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ticketService.getDeletedByProject(projectId, currentUser).stream()
                .map(DeletedTicketResponse::from)
                .toList();
    }
}
