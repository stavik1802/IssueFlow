package com.att.tdp.issueflow.security.jwt;

import com.att.tdp.issueflow.security.auth.CurrentUser;
import com.att.tdp.issueflow.user.User;
import com.att.tdp.issueflow.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final TokenDenyListService tokenDenyListService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(
            JwtTokenService jwtTokenService,
            TokenDenyListService tokenDenyListService,
            UserRepository userRepository
    ) {
        this.jwtTokenService = jwtTokenService;
        this.tokenDenyListService = tokenDenyListService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = extractBearerToken(request);
        if (token != null
                && !tokenDenyListService.isDenied(token)
                && jwtTokenService.isValid(token)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            userRepository.findById(jwtTokenService.getUserId(token))
                    .map(this::toCurrentUser)
                    .ifPresent(currentUser -> authenticate(currentUser, token));
        }

        filterChain.doFilter(request, response);
    }

    private CurrentUser toCurrentUser(User user) {
        return new CurrentUser(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }

    private void authenticate(CurrentUser currentUser, String token) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                currentUser,
                token,
                List.of(new SimpleGrantedAuthority("ROLE_" + currentUser.role().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        return token.isEmpty() ? null : token;
    }
}
