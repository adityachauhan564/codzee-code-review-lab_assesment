package io.codzee.reviewlab.salary;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Exercises SalaryRequest's amount canonicalization together with its real Bean Validation
 * constraints, including BigDecimal exponents at the limits of the int scale.
 */
class SalaryRequestTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void ordinarySalaryIsValid() {
        SalaryRequest request = new SalaryRequest(new BigDecimal("85000.50"), "USD");

        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.amount()).isEqualTo(new BigDecimal("85000.50"));
    }

    @ParameterizedTest
    @CsvSource({
        "9999999999999.99, 9999999999999.99",
        "9999999999999.990, 9999999999999.99",
        "9.99999999999999E+12, 9999999999999.99",
        "0.01, 0.01",
        "1E-2, 0.01"})
    void amountsThatExactlyFitTheColumnAreValidAtScaleTwo(String amount, String expected) {
        SalaryRequest request = new SalaryRequest(new BigDecimal(amount), "USD");

        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.amount()).isEqualTo(new BigDecimal(expected));
    }

    @Test
    void trailingZerosAtAVeryLargeScaleAreValid() {
        BigDecimal amount = new BigDecimal("1500." + "0".repeat(5_000));

        SalaryRequest request = new SalaryRequest(amount, "USD");

        assertThat(VALIDATOR.validate(request)).isEmpty();
        assertThat(request.amount()).isEqualTo(new BigDecimal("1500.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"10000000000000", "10000000000000.00", "1E+13", "9999999999999.995"})
    void amountsExceedingTheIntegerDigitsOrColumnMaximumAreRejected(String amount) {
        assertAmountRejected(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"10.555", "0.001", "1E-3", "10.5550"})
    void amountsNeedingMoreThanTwoDecimalPlacesAreRejected(String amount) {
        assertAmountRejected(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "1E+2147483647",
        "10E+2147483647",
        "100E+2147483647",
        "123456789E+2147483647",
        "1E+2147483646",
        "1E+999999999"})
    void extremePositiveExponentsAreRejectedAsValidationErrors(String amount) {
        assertAmountRejected(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1E+2147483647", "-100E+2147483647", "-9999999999999.99"})
    void negativeAmountsAreRejectedAtAnyMagnitude(String amount) {
        assertAmountRejected(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"1E-2147483647", "10E-2147483647", "123E-2147483647"})
    void extremeNegativeExponentsAreRejectedAsTooManyDecimalPlaces(String amount) {
        assertAmountRejected(amount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0E+2147483647", "0E-2147483647", "0.000"})
    void zeroIsRejectedAtAnyScale(String amount) {
        assertAmountRejected(amount);
    }

    @Test
    void outOfRangeAmountsAreLeftUnchangedRatherThanRounded() {
        BigDecimal amount = new BigDecimal("1E+2147483647");

        assertThat(new SalaryRequest(amount, "USD").amount()).isSameAs(amount);
    }

    @Test
    void missingAmountIsRejected() {
        assertThat(violationsFor(new SalaryRequest(null, "USD")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("amount");
    }

    /** Construction must not throw, and validation must report the problem on the amount field. */
    private static void assertAmountRejected(String amount) {
        SalaryRequest request = new SalaryRequest(new BigDecimal(amount), "USD");

        assertThat(violationsFor(request))
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.getPropertyPath()).hasToString("amount"));
    }

    private static Set<ConstraintViolation<SalaryRequest>> violationsFor(SalaryRequest request) {
        return VALIDATOR.validate(request);
    }
}
