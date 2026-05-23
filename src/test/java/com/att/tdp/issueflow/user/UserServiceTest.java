package com.att.tdp.issueflow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.project.ProjectService;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditEventPublisher auditEventPublisher;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectService projectService;

    private final UserMapper userMapper = new UserMapper();

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userMapper, auditEventPublisher, projectRepository, projectService);
    }

    @Test
    void createsUserSuccessfully() {
        CreateUserRequest request = createRequest();
        when(userRepository.existsByUsername("jdoe")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        UserResponse response = userService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.username()).isEqualTo("jdoe");
        assertThat(response.email()).isEqualTo("jdoe@example.com");
        assertThat(response.fullName()).isEqualTo("Jane Doe");
        assertThat(response.role()).isEqualTo(Role.DEVELOPER);
        assertThat(savedUser.getUsername()).isEqualTo("jdoe");
    }

    @Test
    void createsRegistryUserWithoutCredentialFields() {
        CreateUserRequest request = new CreateUserRequest(
                "jdoe",
                "jdoe@example.com",
                "Jane Doe",
                Role.DEVELOPER
        );
        when(userRepository.existsByUsername("jdoe")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            return user;
        });

        userService.create(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("jdoe");
    }

    @Test
    void rejectsDuplicateUsername() {
        CreateUserRequest request = createRequest();
        when(userRepository.existsByUsername("jdoe")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void rejectsDuplicateEmail() {
        CreateUserRequest request = createRequest();
        when(userRepository.existsByUsername("jdoe")).thenReturn(false);
        when(userRepository.existsByEmail("jdoe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exists");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void throwsNotFoundWhenUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void updatesUserRoleAndFullName() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        UserResponse response = userService.update(
                1L,
                new UpdateUserRequest("Jane Admin", Role.ADMIN)
        );

        assertThat(user.getFullName()).isEqualTo("Jane Admin");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(response.fullName()).isEqualTo("Jane Admin");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void deletesUser() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(projectRepository.findActiveIdsByOwnerId(1L)).thenReturn(List.of());

        userService.delete(1L);

        verify(userRepository).delete(user);
    }

    @Test
    void deletingCurrentUserWritesSystemLogoutAuditEvent() {
        User user = user();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(projectRepository.findActiveIdsByOwnerId(1L)).thenReturn(List.of());

        userService.delete(1L, new CurrentUser(1L, "jdoe", "jdoe@example.com", "Jane Doe", Role.DEVELOPER));

        verify(auditEventPublisher).systemAction(
                1L,
                AuditAction.LOGOUT,
                AuditableEntityType.AUTH,
                1L,
                null,
                null
        );
    }

    @Test
    void credentialFieldsAreNotExposedInResponse() {
        UserResponse response = userMapper.toResponse(user());

        assertThat(response)
                .extracting(UserResponse::username, UserResponse::email, UserResponse::fullName)
                .containsExactly("jdoe", "jdoe@example.com", "Jane Doe");
        assertThat(Arrays.stream(UserResponse.class.getRecordComponents())
                .map(RecordComponent::getName))
                .doesNotContain("password", "passwordHash");
    }

    private static CreateUserRequest createRequest() {
        return new CreateUserRequest(
                "jdoe",
                "jdoe@example.com",
                "Jane Doe",
                Role.DEVELOPER
        );
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        user.setUsername("jdoe");
        user.setEmail("jdoe@example.com");
        user.setFullName("Jane Doe");
        user.setRole(Role.DEVELOPER);
        return user;
    }
}
