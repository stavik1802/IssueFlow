package com.att.tdp.issueflow.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query("""
            select count(u) > 0
            from User u
            where u.username = :username
              and u.email = :email
              and u.fullName = :fullName
              and u.role = :role
            """)
    boolean existsDuplicateActiveUser(
            @Param("username") String username,
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("role") Role role
    );
}
