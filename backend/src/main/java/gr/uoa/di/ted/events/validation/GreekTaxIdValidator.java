package gr.uoa.di.ted.events.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Modulo-11 check-digit algorithm for the Greek AFM.
 *
 * Take the first 8 digits, weight them by 256,128,64,32,16,8,4,2 (i.e. 2^(8-i)) and
 * sum. The check digit is (sum mod 11) mod 10; the extra mod 10 turns the rare
 * sum-mod-11 == 10 into 0. The 9th digit must equal that check.
 *
 * e.g. AFM "094259216": weighted sum = 1634, 1634 mod 11 = 6, 6 mod 10 = 6, and the
 * last digit is 6, so it's valid.
 */
public class GreekTaxIdValidator implements ConstraintValidator<GreekTaxId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isBlank()) return true; // @NotBlank handles the empty case

        if (!value.matches("\\d{9}")) return false;

        int sum = 0;
        for (int i = 0; i < 8; i++) {
            sum += (value.charAt(i) - '0') << (8 - i); // digit * 2^(8-i)
        }
        int check = (sum % 11) % 10;
        return check == (value.charAt(8) - '0');
    }
}
