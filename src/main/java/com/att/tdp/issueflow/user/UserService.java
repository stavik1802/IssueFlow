package com.att.tdp.issueflow.user;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditEventPublisher;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.common.exception.ConflictException;
import com.att.tdp.issueflow.common.exception.NotFoundException;
import com.att.tdp.issueflow.project.ProjectRepository;
import com.att.tdp.issueflow.project.ProjectService;
import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.user.dto.CreateUserRequest;
import com.att.tdp.issueflow.user.dto.UpdateUserRequest;
import com.att.tdp.issueflow.user.dto.UserResponse;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final String USER_NOT_FOUND = "User not found";

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;

    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            AuditEventPublisher auditEventPublisher,
            ProjectRepository projectRepository,
            ProjectService projectService
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.auditEventPublisher = auditEventPublisher;
        this.projectRepository = projectRepository;
        this.projectService = projectService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        return create(request, null);
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, CurrentUser currentUser) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        String fullName = request.fullName().trim();

        if (userRepository.existsDuplicateActiveUser(username, email, fullName, request.role())) {
            throw new ConflictException("Duplicate user");
        }
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username already exists");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }

        User user = userMapper.toEntity(request);
        try {
            UserResponse response = userMapper.toResponse(userRepository.save(user));
            auditEventPublisher.userAction(
                    currentUser == null ? response.id() : currentUser.id(),
                    AuditAction.CREATE,
                    AuditableEntityType.USER,
                    response.id(),
                    null,
                    response
            );
            return response;
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Username or email already exists", exception);
        }
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findUser(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(userMapper::toResponse)
                .toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request) {
        return update(id, request, null);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, CurrentUser currentUser) {
        User user = findUser(id);
        UserResponse oldValue = userMapper.toResponse(user);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        UserResponse response = userMapper.toResponse(user);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.UPDATE,
                AuditableEntityType.USER,
                id,
                oldValue,
                response
        );
        return response;
    }

    @Transactional
    public void delete(Long id) {
        delete(id, null);
    }

    @Transactional
    public void delete(Long id, CurrentUser currentUser) {
        User user = findUser(id);
        UserResponse oldValue = userMapper.toResponse(user);
        projectRepository.findActiveIdsByOwnerId(id)
                .forEach(projectId -> projectService.delete(projectId, currentUser));
        userRepository.delete(user);
        auditEventPublisher.userAction(
                currentUser == null ? null : currentUser.id(),
                AuditAction.DELETE,
                AuditableEntityType.USER,
                id,
                oldValue,
                null
        );
        if (currentUser != null && currentUser.id().equals(id)) {
            auditEventPublisher.systemAction(
                    currentUser.id(),
                    AuditAction.LOGOUT,
                    AuditableEntityType.AUTH,
                    id,
                    null,
                    null
            );
        }
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(USER_NOT_FOUND));
    }

}
