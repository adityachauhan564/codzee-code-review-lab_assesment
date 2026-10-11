package io.codzee.reviewlab.salary;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Payload for setting an employee's current salary. Amounts with more than two decimal places are
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
}
