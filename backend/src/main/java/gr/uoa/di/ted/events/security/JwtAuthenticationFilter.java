package gr.uoa.di.ted.events.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stateless JWT authentication filter. Runs once per request, before Spring's
 * UsernamePasswordAuthenticationFilter. For a valid Bearer token it reads the
 * username, loads the UserDetails from the DB to confirm the account is still
 * active, populates the SecurityContext (so @PreAuthorize works), and, when
 * mustChangePassword is set, blocks every route except change-password with a
 * 403 PASSWORD_CHANGE_REQUIRED.
 *
 * Expired or malformed tokens are ignored; the SecurityContext stays empty and the
 * downstream authorisation check returns 401.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String CHANGE_PASSWORD_PATH = "/api/v1/auth/change-password";

    private final JwtService              jwtService;
    private final UserDetailsServiceImpl  userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         chain) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            String username = jwtService.extractUsername(token);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetailsImpl principal =
                        (UserDetailsImpl) userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, principal)) {
                    // populate the SecurityContext so controllers can use @AuthenticationPrincipal
                    var auth = new UsernamePasswordAuthenticationToken(
                            principal, null, principal.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    // password-change gate: block everything except change-password
                    // until the user updates their initial/reset password.
                    if (principal.isMustChangePassword()
                            && !CHANGE_PASSWORD_PATH.equals(request.getServletPath())) {
                        writeError(response, HttpStatus.FORBIDDEN,
                                "PASSWORD_CHANGE_REQUIRED",
                                "You must change your password before accessing this resource.");
                        return;
                    }
                }
            }
        } catch (JwtException ignored) {
            // invalid token: leave SecurityContext empty so downstream returns 401
        }

        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                String.format("{\"code\":\"%s\",\"message\":\"%s\"}", code, message));
    }
}
