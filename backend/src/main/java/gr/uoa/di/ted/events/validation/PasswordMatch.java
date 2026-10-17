package gr.uoa.di.ted.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Class-level constraint that verifies the password and confirmPassword fields
 * of a {@link gr.uoa.di.ted.events.dto.auth.RegisterRequest} record are equal.
 *
 * Placed on the record TYPE (not a single field) because it needs to read both fields.
 * The validator adds the violation to the confirmPassword property so the frontend
 * can display the error next to the correct form field.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = PasswordMatchValidator.class)
public @interface PasswordMatch {
    String message() default "Passwords do not match";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
