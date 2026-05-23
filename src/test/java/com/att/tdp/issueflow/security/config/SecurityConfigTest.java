package com.att.tdp.issueflow.security.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.att.tdp.issueflow.security.jwt.JwtTokenService;
import com.att.tdp.issueflow.user.Role;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
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

    @Test
    void protectedEndpointRequiresJwt() throws Exception {
        mockMvc.perform(get("/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationIsPublic() throws Exception {
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
                .andExpect(status().isOk());
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
