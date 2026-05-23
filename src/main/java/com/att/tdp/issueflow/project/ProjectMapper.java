package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.project.dto.CreateProjectRequest;
import com.att.tdp.issueflow.project.dto.ProjectResponse;
import com.att.tdp.issueflow.user.User;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public Project toEntity(CreateProjectRequest request, User owner, String key) {
        Project project = new Project();
        project.setKey(key);
        project.setName(request.name().trim());
        project.setDescription(trimToNull(request.description()));
        project.setOwner(owner);

        ProjectMember ownerMember = new ProjectMember();
        ownerMember.setUser(owner);
        ownerMember.setRole(ProjectMemberRole.OWNER);
        project.addMember(ownerMember);

        return project;
    }

    public ProjectResponse toResponse(Project project) {
        User owner = project.getOwner();
        return new ProjectResponse(
                project.getId(),
                project.getKey(),
                project.getName(),
                project.getDescription(),
                owner.getId(),
                owner.getUsername(),
                project.getMembers().size(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getDeletedAt()
        );
    }

    public String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
