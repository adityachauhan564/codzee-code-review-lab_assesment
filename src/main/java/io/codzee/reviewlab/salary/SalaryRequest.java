package io.codzee.reviewlab.salary;

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
        @Digits(integer = Salary.AMOUNT_INTEGER_DIGITS, fraction = Salary.AMOUNT_SCALE)
        BigDecimal amount,

        @NotNull
        @Pattern(regexp = Salary.SUPPORTED_CURRENCY,
                message = "must be " + Salary.SUPPORTED_CURRENCY + ", the only supported currency")
        String currency) {

    public SalaryRequest {
        amount = toAmountScale(amount);
    }

    /**
     * Rewrites amounts that are exactly representable in the salary column (1500.500, 1.5E+3) to
     * scale 2, so equal values are equal regardless of notation. Anything else is returned untouched
     * for the constraints to reject; nothing is ever rounded. The range check runs before setScale so
     * an input like 1E+999999999 cannot force a huge allocation.
     */
    private static BigDecimal toAmountScale(BigDecimal amount) {
        if (amount == null) {
            return null;
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        boolean fitsColumn = stripped.scale() <= Salary.AMOUNT_SCALE
                && stripped.precision() - stripped.scale() <= Salary.AMOUNT_INTEGER_DIGITS;
        return fitsColumn ? stripped.setScale(Salary.AMOUNT_SCALE) : amount;
    }
}
