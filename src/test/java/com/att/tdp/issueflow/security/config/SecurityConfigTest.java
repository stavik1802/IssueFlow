package com.att.tdp.issueflow.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.audit.AuditAction;
import com.att.tdp.issueflow.audit.AuditActorType;
import com.att.tdp.issueflow.audit.AuditLogRepository;
import com.att.tdp.issueflow.audit.AuditableEntityType;
import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void protectedEndpointRequiresJwt() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void seededAdminExistsAndCanLoginWithConfiguredGlobalPassword() throws Exception {
        User admin = userRepository.findByUsername("admin").orElseThrow();

        Assertions.assertThat(admin.getId()).isZero();
        Assertions.assertThat(admin.getEmail()).isEqualTo("admin@issueflow.com");
        Assertions.assertThat(admin.getFullName()).isEqualTo("System Admin");
        Assertions.assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        Assertions.assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            Assertions.assertThat(log.getActorType()).isEqualTo(AuditActorType.SYSTEM);
            Assertions.assertThat(log.getActorId()).isEqualTo(admin.getId());
            Assertions.assertThat(log.getAction()).isEqualTo(AuditAction.CREATE);
            Assertions.assertThat(log.getEntityType()).isEqualTo(AuditableEntityType.USER);
            Assertions.assertThat(log.getEntityId()).isEqualTo(admin.getId());
        });

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600));

        mockMvc.perform(get("/auth/me")
                        .header("Authorization", "Bearer " + jwtTokenService.generateToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(admin.getId()))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void registrationRequiresJwt() throws Exception {
        mockMvc.perform(post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "public-readme-user",
                                  "email": "public-readme-user@example.com",
                                  "fullName": "Public User",
                                  "role": "DEVELOPER"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationAcceptsJwt() throws Exception {
        mockMvc.perform(post("/users")
                        .header("Authorization", "Bearer " + token(Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "protecteduser",
                                  "email": "protecteduser@example.com",
                                  "fullName": "Protected User",
                                  "role": "DEVELOPER"
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void restoreEndpointRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/projects/1/restore")
                        .header("Authorization", "Bearer " + token(Role.DEVELOPER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedEndpointRequiresAdminRole() throws Exception {
        mockMvc.perform(get("/tickets/deleted?projectId=1")
                        .header("Authorization", "Bearer " + token(Role.DEVELOPER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deletedUserTokenNoLongerAuthenticates() throws Exception {
        User user = savedUser(Role.DEVELOPER);
        String token = jwtTokenService.generateToken(user);

        userRepository.delete(user);
        userRepository.flush();

        mockMvc.perform(get("/projects")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String token(Role role) {
        return jwtTokenService.generateToken(savedUser(role));
    }

    private User savedUser(Role role) {
        User user = new User();
        String suffix = role.name().toLowerCase() + "-" + System.nanoTime();
        user.setUsername("token-" + suffix);
        user.setEmail("token-" + suffix + "@example.com");
        user.setFullName(role.name());
        user.setRole(role);
        user.setActive(true);
        return userRepository.saveAndFlush(user);
    }
}
