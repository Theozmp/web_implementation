package gr.uoa.di.ted.events.dto.auth;

import gr.uoa.di.ted.events.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @ValidPassword
        String newPassword,

        @NotBlank(message = "Please confirm your new password")
        String confirmNewPassword
) {}
