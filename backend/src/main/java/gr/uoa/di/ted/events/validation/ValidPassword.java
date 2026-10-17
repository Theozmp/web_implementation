package gr.uoa.di.ted.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Password complexity: at least 8 characters with a letter and a digit. Null
 * passes (pair with @NotBlank).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
public @interface ValidPassword {
    String message() default "Password must be at least 8 characters and contain at least one letter and one digit";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
