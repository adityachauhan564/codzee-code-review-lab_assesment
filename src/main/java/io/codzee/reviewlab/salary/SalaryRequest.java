package io.codzee.reviewlab.salary;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Payload for setting an employee's current salary. Amounts needing more than two decimal places are
 * rejected rather than rounded, and the currency must be stated explicitly.
 */
public record SalaryRequest(
        @NotNull
        @DecimalMin(value = "0", inclusive = false, message = "must be greater than 0")
        // @DecimalMax enforces the range with compareTo; @Digits' integer-length check uses int
        // arithmetic that overflows for exponents near Integer.MAX_VALUE and would accept them.
        @DecimalMax(Salary.MAX_AMOUNT)
        @Digits(integer = Salary.AMOUNT_INTEGER_DIGITS, fraction = Salary.AMOUNT_SCALE)
        BigDecimal amount,

        @NotNull
        @Pattern(regexp = Salary.SUPPORTED_CURRENCY,
                message = "must be " + Salary.SUPPORTED_CURRENCY + ", the only supported currency")
        String currency) {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal(Salary.MAX_AMOUNT);

    public SalaryRequest {
        amount = toAmountScale(amount);
    }

    /**
     * Rewrites amounts that are exactly representable in the salary column (1500.500, 1.5E+3) to
     * scale 2, so equal values are equal regardless of notation. Anything else is returned untouched
     * for the constraints to reject; nothing is ever rounded.
     *
     * <p>The magnitude is checked with compareTo first because it cannot overflow. Only within the
     * column range is the scale small enough for stripTrailingZeros and setScale to be safe; for an
     * input like 1E+2147483647 they throw ArithmeticException, and precision() - scale() overflows int.
     */
    private static BigDecimal toAmountScale(BigDecimal amount) {
        if (amount == null || amount.abs().compareTo(MAX_AMOUNT) > 0) {
            return amount;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        return stripped.scale() <= Salary.AMOUNT_SCALE ? stripped.setScale(Salary.AMOUNT_SCALE) : amount;
    }
}
