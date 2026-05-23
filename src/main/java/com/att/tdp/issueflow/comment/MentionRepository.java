package com.att.tdp.issueflow.comment;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MentionRepository extends JpaRepository<Mention, Long> {

    @EntityGraph(attributePaths = {"comment", "comment.author", "comment.ticket", "comment.mentions", "comment.mentions.mentionedUser"})
    @Query("""
            select m
            from Mention m
            where m.mentionedUser.id = :mentionedUserId
              and m.comment.deletedAt is null
              and m.ticket.deletedAt is null
              and m.ticket.project.deletedAt is null
            order by m.comment.createdAt desc
            """)
    List<Mention> findActiveByMentionedUserIdOrderByCommentCreatedAtDesc(@Param("mentionedUserId") Long mentionedUserId);
}
