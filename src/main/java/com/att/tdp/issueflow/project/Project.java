package com.att.tdp.issueflow.project;

import com.att.tdp.issueflow.common.persistence.SoftDeletableAuditableEntity;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.User;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "projects",
        uniqueConstraints = @UniqueConstraint(name = "uk_projects_key", columnNames = "project_key"),
        indexes = @Index(name = "idx_projects_owner_id", columnList = "owner_id")
)
@SQLDelete(sql = "UPDATE projects SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class Project extends SoftDeletableAuditableEntity {

    @NotBlank
    @Column(name = "project_key", nullable = false, length = 32)
    private String key;

    @NotBlank
    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, foreignKey = @ForeignKey(name = "fk_projects_owner"))
    private User owner;

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMember> members = new LinkedHashSet<>();

    @JsonIgnore
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "project")
    private Set<Ticket> tickets = new LinkedHashSet<>();

    public void addMember(ProjectMember member) {
        members.add(member);
        member.setProject(this);
    }

    public void removeMember(ProjectMember member) {
        members.remove(member);
        member.setProject(null);
    }
}
