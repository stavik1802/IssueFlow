package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.attachment.Attachment;
import com.att.tdp.issueflow.comment.Comment;
import com.att.tdp.issueflow.comment.Mention;
import com.att.tdp.issueflow.common.persistence.SoftDeletableAuditableEntity;
import com.att.tdp.issueflow.project.Project;
import com.att.tdp.issueflow.project.ProjectMember;
import com.att.tdp.issueflow.ticket.Ticket;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class User extends SoftDeletableAuditableEntity {

    @NotBlank
    @Column(nullable = false, length = 80)
    private String username;

    @Email
    @NotBlank
    @Column(nullable = false, length = 320)
    private String email;

    @NotBlank
    @Column(name = "display_name", nullable = false, length = 160)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Role role = Role.DEVELOPER;

    @Column(nullable = false)
    private boolean active = true;

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "owner")
    private Set<Project> ownedProjects = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "user")
    private Set<ProjectMember> memberships = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "reporter")
    private Set<Ticket> reportedTickets = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "assignee")
    private Set<Ticket> assignedTickets = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "author")
    private Set<Comment> comments = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "mentionedUser")
    private Set<Mention> mentions = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "uploadedBy")
    private Set<Attachment> attachments = new LinkedHashSet<>();
}
