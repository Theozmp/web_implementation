package gr.uoa.di.ted.events.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * Handles JWT creation and verification with HS256 (HMAC-SHA256). The secret must
 * be at least 256 bits; the dev default in application.yml is long enough.
 *
 * Token claims: sub (username), uid (DB user id, so the filter skips a lookup),
 * roles (Spring authority strings like ["ROLE_ORGANIZER"]), mustChangePassword
 * (true for the admin on first login), plus the standard iat/exp.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long      expirationMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key          = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // token generation

    public String generateToken(UserDetailsImpl principal) {
        Instant now = Instant.now();
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        return Jwts.builder()
                .subject(principal.getUsername())
                .claim("uid",               principal.getUserId())
                .claim("roles",             roles)
                .claim("mustChangePassword", principal.isMustChangePassword())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    // token validation

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = parseClaims(token);
            return claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date());
        } catch (JwtException e) {
            return false;
        }
    }

    // claim extraction

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Reads the roles list straight from the token, skipping a DB round-trip in the filter. */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return (List<String>) parseClaims(token).get("roles");
    }

    public boolean extractMustChangePassword(String token) {
        return Boolean.TRUE.equals(parseClaims(token).get("mustChangePassword", Boolean.class));
    }

    public long getExpirationMs() { return expirationMs; }

    // internal

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
