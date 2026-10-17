package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.auth.*;
import gr.uoa.di.ted.events.security.UserDetailsImpl;
import gr.uoa.di.ted.events.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "Registration, login, and password management")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user account",
               description = "Creates a PENDING account. An admin must approve it before login is possible.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@Valid @RequestBody RegisterRequest req) {
        authService.register(req);
        return Map.of("message",
                "Registration successful. Your account is pending admin approval. " +
                "You will be able to log in once an administrator reviews your request.");
    }

    @Operation(summary = "Log in and receive a JWT",
               description = "Returns accessToken + expiry + basic profile. " +
                             "Status PENDING → 403. Status REJECTED → 403.")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    @Operation(summary = "Change the authenticated user's password",
               description = "Required on first login when mustChangePassword == true. " +
                             "The JWT filter blocks all other endpoints until this is called.")
    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        authService.changePassword(principal.getUsername(), req);
    }
}
