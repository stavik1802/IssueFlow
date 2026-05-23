package com.att.tdp.issueflow.project;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByKey(String key);

    @Query("""
            select p.id
            from Project p
            where p.owner.id = :ownerId
            """)
    List<Long> findActiveIdsByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
            select count(p) > 0
            from Project p
            where p.name = :name
              and p.description = :description
              and p.owner.id = :ownerId
            """)
    boolean existsDuplicateActiveProject(
            @Param("name") String name,
            @Param("description") String description,
            @Param("ownerId") Long ownerId
    );

    @Query(value = "SELECT * FROM projects WHERE id = :id", nativeQuery = true)
    Optional<Project> findIncludingDeletedById(@Param("id") Long id);

    @Query(value = "SELECT * FROM projects WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC", nativeQuery = true)
    List<Project> findDeletedProjects();

    @Modifying
    @Query(value = "UPDATE projects SET deleted_at = NULL, deleted_by = NULL WHERE id = :id AND deleted_at IS NOT NULL", nativeQuery = true)
    int restoreById(@Param("id") Long id);
}
