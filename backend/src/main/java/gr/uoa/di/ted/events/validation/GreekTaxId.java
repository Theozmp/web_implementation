package gr.uoa.di.ted.events.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates a Greek Tax Identification Number (AFM): exactly 9 digits passing the
 * modulo-11 check digit. Null or blank passes (pair with @NotBlank to reject blanks).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = GreekTaxIdValidator.class)
public @interface GreekTaxId {
    String message() default "Invalid Greek tax identification number (ΑΦΜ): must be 9 digits with a valid check digit";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
