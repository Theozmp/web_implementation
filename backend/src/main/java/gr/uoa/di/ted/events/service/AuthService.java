package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.auth.*;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.exception.*;
import gr.uoa.di.ted.events.repository.UserRepository;
import gr.uoa.di.ted.events.security.JwtService;
import gr.uoa.di.ted.events.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;

    // register

    /**
     * Creates a new user account in PENDING state with the role the applicant chose
     * (ORGANIZER or PARTICIPANT). ORGANIZER grants both ROLE_ORGANIZER (create events)
     * and ROLE_PARTICIPANT (book tickets); PARTICIPANT grants only ROLE_PARTICIPANT
     * (see UserDetailsImpl.buildAuthorities). ADMIN is rejected: nobody can
     * self-register as an administrator. An admin must approve before login.
     */
    @Transactional
    public void register(RegisterRequest req) {
        if (req.role() == Role.ADMIN) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_ROLE",
                    "You cannot register as an administrator");
        }
        if (userRepository.existsByUsername(req.username())) {
            throw new UsernameConflictException("Username '" + req.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailConflictException("Email '" + req.email() + "' is already registered");
        }

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setPhone(req.phone());
        user.setAddressLine(req.addressLine());
        user.setCity(req.city());
        user.setCountry(req.country());
        user.setLatitude(req.latitude());
        user.setLongitude(req.longitude());
        user.setTaxId(req.taxId());
        user.setRole(req.role());
        user.setStatus(UserStatus.PENDING);
        user.setMustChangePassword(false);

        userRepository.save(user);
    }

    // login

    /**
     * Verifies credentials and returns a signed JWT.
     *
     * We check status before the password so the message is accurate: PENDING gives
     * 403 ACCOUNT_PENDING (not 401, since the credentials may be right), REJECTED
     * gives 403 ACCOUNT_REJECTED, and only an active account gets the password
     * check and a token. We avoid Spring's AuthenticationManager here because its
     * Disabled/Locked exception messages are too generic for the per-status
     * messages the spec wants.
     */
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() == UserStatus.PENDING)  throw new AccountNotApprovedException();
        if (user.getStatus() == UserStatus.REJECTED) throw new AccountRejectedException();

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UserDetailsImpl principal = new UserDetailsImpl(user);
        String token    = jwtService.generateToken(principal);
        Instant expires = Instant.now().plusMillis(jwtService.getExpirationMs());

        return new AuthResponse(token, expires,
                new AuthResponse.UserSummary(
                        user.getId(), user.getUsername(),
                        user.getFirstName(), user.getLastName(),
                        user.getEmail(), user.getRole().name(),
                        user.isMustChangePassword()));
    }

    // change password

    @Transactional
    public void changePassword(String username, ChangePasswordRequest req) {
        if (!req.newPassword().equals(req.confirmNewPassword())) {
            throw new PasswordMismatchException("New passwords do not match");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setMustChangePassword(false);
        // updatedAt is set automatically by @LastModifiedDate in BaseEntity
    }
}
