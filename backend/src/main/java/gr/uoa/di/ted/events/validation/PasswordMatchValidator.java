package gr.uoa.di.ted.events.validation;

import gr.uoa.di.ted.events.dto.auth.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordMatchValidator implements ConstraintValidator<PasswordMatch, RegisterRequest> {

    @Override
    public boolean isValid(RegisterRequest req, ConstraintValidatorContext ctx) {
        if (req == null || req.password() == null) return true;

        boolean match = req.password().equals(req.confirmPassword());
        if (!match) {
            // Pin the violation to confirmPassword so the frontend knows which field to highlight
            ctx.disableDefaultConstraintViolation();
            ctx.buildConstraintViolationWithTemplate("Passwords do not match")
               .addPropertyNode("confirmPassword")
               .addConstraintViolation();
        }
        return match;
    }
}
