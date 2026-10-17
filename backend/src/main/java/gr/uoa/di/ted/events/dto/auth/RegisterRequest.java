package gr.uoa.di.ted.events.dto.auth;

import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.validation.GreekTaxId;
import gr.uoa.di.ted.events.validation.PasswordMatch;
import gr.uoa.di.ted.events.validation.ValidPassword;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Registration payload.
 *
 * Validation order: field-level constraints (@NotBlank, @Size, ...) run first, then
 * the class-level @PasswordMatch, which pins its violation to confirmPassword so the
 * frontend shows it on the right field. @GreekTaxId runs the AFM modulo-11 check and
 * @ValidPassword enforces complexity (8+ chars, a letter, a digit).
 *
 * It's a record, so the DTO is immutable with no getter boilerplate.
 */
@PasswordMatch
public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 4, max = 30, message = "Username must be 4–30 characters")
        @Pattern(regexp = "[a-zA-Z0-9_]+",
                 message = "Username may only contain letters, digits and underscores")
        String username,

        @NotBlank(message = "Password is required")
        @ValidPassword
        String password,

        @NotBlank(message = "Please confirm your password")
        String confirmPassword,

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Please enter a valid email address")
        String email,

        @Size(max = 30)
        String phone,

        @Size(max = 255)
        String addressLine,

        @Size(max = 100)
        String city,

        @Size(max = 100)
        String country,

        @DecimalMin(value = "-90.0",  message = "Latitude must be between −90 and +90")
        @DecimalMax(value = "90.0",   message = "Latitude must be between −90 and +90")
        BigDecimal latitude,

        @DecimalMin(value = "-180.0", message = "Longitude must be between −180 and +180")
        @DecimalMax(value = "180.0",  message = "Longitude must be between −180 and +180")
        BigDecimal longitude,

        @NotBlank(message = "Tax ID (ΑΦΜ) is required")
        @GreekTaxId
        String taxId,

        // The role the user wants to register under. Only ORGANIZER or PARTICIPANT
        // are accepted; AuthService rejects ADMIN (you can't self-register as admin).
        @NotNull(message = "Please choose a role")
        Role role
) {}
