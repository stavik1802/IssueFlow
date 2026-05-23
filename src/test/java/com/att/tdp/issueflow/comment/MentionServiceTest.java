package com.att.tdp.issueflow.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.comment.dto.CommentResponse;
import com.att.tdp.issueflow.common.exception.BadRequestException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.ticket.Ticket;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MentionServiceTest {

    @Mock
    private MentionRepository mentionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    private MentionService mentionService;

    @BeforeEach
    void setUp() {
        mentionService = new MentionService(
                mentionRepository,
                userRepository,
                new MentionParser(),
                new CommentMapper(),
                auditEventPublisher
        );
    }

    @Test
    void syncMentionsMatchesUsersCaseInsensitively() {
        Comment comment = comment("Hi @ALICE and @alice", author("author"));
        when(userRepository.findAll()).thenReturn(List.of(author("alice")));

        mentionService.syncMentions(comment);

        assertThat(comment.getMentions())
                .singleElement()
                .satisfies(mention -> assertThat(mention.getMentionedUser().getUsername()).isEqualTo("alice"));
    }

    @Test
    void syncMentionsRejectsMissingUsers() {
        Comment comment = comment("Hi @missing", author("author"));
        when(userRepository.findAll()).thenReturn(List.of(author("alice")));

        assertThatThrownBy(() -> mentionService.syncMentions(comment))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unknown mentioned user: missing");
    }

    @Test
    void syncMentionsRemovesOldMentionsOnUpdate() {
        User alice = author("alice");
        User bob = author("bob");
        Comment comment = comment("Hi @alice", author("author"));
        Mention oldMention = mention(comment, alice);
        comment.getMentions().add(oldMention);
        comment.setBody("Hi @bob");
        when(userRepository.findAll()).thenReturn(List.of(alice, bob));

        mentionService.syncMentions(comment);

        assertThat(comment.getMentions())
                .singleElement()
                .satisfies(mention -> assertThat(mention.getMentionedUser().getUsername()).isEqualTo("bob"));
    }

    @Test
    void mentionedCommentsAreReturnedNewestFirst() {
        User mentionedUser = author("alice");
        Comment older = comment("older @alice", author("author"));
        older.setId(1L);
        older.setCreatedAt(Instant.parse("2026-05-19T10:00:00Z"));
        Mention olderMention = mention(older, mentionedUser);
        older.getMentions().add(olderMention);

        Comment newer = comment("newer @alice", author("author"));
        newer.setId(2L);
        newer.setCreatedAt(Instant.parse("2026-05-19T11:00:00Z"));
        Mention newerMention = mention(newer, mentionedUser);
        newer.getMentions().add(newerMention);

        when(userRepository.existsById(mentionedUser.getId())).thenReturn(true);
        when(mentionRepository.findActiveByMentionedUserIdOrderByCommentCreatedAtDesc(mentionedUser.getId()))
                .thenReturn(List.of(olderMention, newerMention));

        List<CommentResponse> responses = mentionService.getCommentsMentioningUser(mentionedUser.getId());

        assertThat(responses)
                .extracting(CommentResponse::id)
                .containsExactly(2L, 1L);
    }

    @Test
    void mentionedCommentsRejectUnknownUser() {
        when(userRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> mentionService.getCommentsMentioningUser(404L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    private static Mention mention(Comment comment, User user) {
        Mention mention = new Mention();
        mention.setComment(comment);
        mention.setTicket(comment.getTicket());
        mention.setMentionedUser(user);
        return mention;
    }

    private static Comment comment(String body, User author) {
        Ticket ticket = new Ticket();
        ticket.setId(10L);
        Comment comment = new Comment();
        comment.setTicket(ticket);
        comment.setAuthor(author);
        comment.setBody(body);
        return comment;
    }

    private static User author(String username) {
        User user = new User();
        user.setId(switch (username) {
            case "alice" -> 1L;
            case "bob" -> 2L;
            default -> 99L;
        });
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setRole(Role.DEVELOPER);
        return user;
    }
}
