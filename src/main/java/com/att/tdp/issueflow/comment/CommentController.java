package com.att.tdp.issueflow.comment;

import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.comment.dto.CreateCommentRequest;
import com.att.tdp.issueflow.comment.dto.UpdateCommentRequest;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
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
public class CommentController {

    private final CommentService commentService;
    private final MentionService mentionService;

    public CommentController(CommentService commentService, MentionService mentionService) {
        this.commentService = commentService;
        this.mentionService = mentionService;
    }

    @PostMapping("/tickets/{ticketId}/comments")
    public ResponseEntity<CommentResponse> create(
            @PathVariable Long ticketId,
            @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        CommentResponse response = commentService.create(ticketId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tickets/{ticketId}/comments")
    public List<CommentResponse> getCommentsForTicket(@PathVariable Long ticketId) {
        return commentService.getCommentsForTicket(ticketId);
    }

    @PatchMapping("/tickets/{ticketId}/comments/{commentId}")
    public ResponseEntity<Void> updateReadmeRoute(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        commentService.update(ticketId, commentId, request, currentUser);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tickets/{ticketId}/comments/{commentId}")
    public ResponseEntity<Void> deleteReadmeRoute(
            @PathVariable Long ticketId,
            @PathVariable Long commentId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        commentService.delete(ticketId, commentId, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}/mentions")
    public Map<String, Object> getCommentsMentioningUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        List<CommentResponse> mentions = mentionService.getCommentsMentioningUser(userId);
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((normalizedPage - 1) * normalizedPageSize, mentions.size());
        int toIndex = Math.min(fromIndex + normalizedPageSize, mentions.size());
        return Map.of(
                "data", mentions.subList(fromIndex, toIndex),
                "total", mentions.size(),
                "page", normalizedPage
        );
    }
}
