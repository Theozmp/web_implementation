package gr.uoa.di.ted.events.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class ValidPasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern HAS_DIGIT  = Pattern.compile("\\d");
    private static final Pattern HAS_LETTER = Pattern.compile("[a-zA-Z]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;
        return value.length() >= 8
                && HAS_DIGIT.matcher(value).find()
                && HAS_LETTER.matcher(value).find();
    }
}
