package com.att.tdp.issueflow.attachment;

import com.att.tdp.issueflow.attachment.dto.AttachmentResponse;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(path = "/tickets/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> upload(
            @PathVariable Long ticketId,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        AttachmentResponse response = attachmentService.upload(ticketId, file, currentUser);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/tickets/{ticketId}/attachments/{attachmentId}")
    public ResponseEntity<Void> deleteReadmeRoute(
            @PathVariable Long ticketId,
            @PathVariable Long attachmentId,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        attachmentService.delete(ticketId, attachmentId, currentUser);
        return ResponseEntity.ok().build();
    }
}
