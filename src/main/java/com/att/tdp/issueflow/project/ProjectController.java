package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.project.dto.UpdateProjectRequest;
import com.att.tdp.issueflow.project.dto.WorkloadResponse;
import com.att.tdp.issueflow.security.auth.CurrentUser;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final WorkloadService workloadService;

    public ProjectController(ProjectService projectService, WorkloadService workloadService) {
        this.projectService = projectService;
        this.workloadService = workloadService;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(
            @Valid @RequestBody CreateProjectRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        ProjectResponse response = projectService.create(request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ProjectResponse getById(@PathVariable Long id) {
        return projectService.getById(id);
    }

    @GetMapping
    public List<ProjectResponse> getAll() {
        return projectService.getAll();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        projectService.update(id, request, currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        projectService.delete(id, currentUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        projectService.restore(id, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/deleted")
    public List<ProjectResponse> getDeletedProjects(@AuthenticationPrincipal CurrentUser currentUser) {
        return projectService.getDeletedProjects(currentUser);
    }

    @GetMapping("/{projectId}/workload")
    public List<WorkloadResponse.MemberWorkload> getWorkload(@PathVariable Long projectId) {
        return workloadService.getWorkload(projectId).members();
    }
}
