package io.codzee.reviewlab.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.codzee.reviewlab.common.ResourceNotFoundException;
import io.codzee.reviewlab.employee.Employee;
import io.codzee.reviewlab.employee.EmployeeRepository;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class SalaryServiceTest {

    private static final long EMPLOYEE_ID = 7L;
    private static final Instant LAST_CHANGE = Instant.parse("2026-01-01T00:00:00Z");

    private SalaryRepository salaryRepository;
    private EmployeeRepository employeeRepository;
    private SalaryService salaryService;

    @BeforeEach
    void setUp() {
        salaryRepository = mock(SalaryRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        salaryService = new SalaryService(salaryRepository, employeeRepository,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Nested
    class SetSalary {

        @Test
        void createsSalaryForEmployeeWithoutOne() {
            Employee employee = employee(EMPLOYEE_ID);
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());
            when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
            when(salaryRepository.save(any(Salary.class))).thenAnswer(invocation -> invocation.getArgument(0));

            SalaryResponse response = salaryService.setSalary(EMPLOYEE_ID, request("1234.50"));

            ArgumentCaptor<Salary> saved = ArgumentCaptor.forClass(Salary.class);
            verify(salaryRepository).save(saved.capture());
            assertThat(saved.getValue().getAmount()).isEqualTo(new BigDecimal("1234.50"));
            assertThat(saved.getValue().getCurrency()).isEqualTo("USD");
            assertThat(response.amount()).isEqualTo(new BigDecimal("1234.50"));
            assertThat(response.updatedAt()).isNotNull();
        }

        @Test
        void replacesExistingSalaryInPlace() {
            Salary existing = salary(EMPLOYEE_ID, "1000.00");
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));

            SalaryResponse response = salaryService.setSalary(EMPLOYEE_ID, request("2000.00"));

            assertThat(existing.getAmount()).isEqualTo(new BigDecimal("2000.00"));
            assertThat(response).extracting(SalaryResponse::employeeId, SalaryResponse::amount)
                    .containsExactly(EMPLOYEE_ID, new BigDecimal("2000.00"));
            verify(salaryRepository, never()).save(any());
        }

        @ParameterizedTest
        @ValueSource(strings = {"1000", "1000.00", "1000.000", "1E+3"})
        void settingTheSameSalaryAgainKeepsUpdatedAt(String sameAmount) {
            Salary existing = salary(EMPLOYEE_ID, "1000.00");
            ReflectionTestUtils.setField(existing, "updatedAt", LAST_CHANGE);
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));

            SalaryResponse response = salaryService.setSalary(EMPLOYEE_ID, request(sameAmount));

            assertThat(response.updatedAt()).isEqualTo(LAST_CHANGE);
            assertThat(existing.getUpdatedAt()).isEqualTo(LAST_CHANGE);
        }

        @Test
        void changingTheAmountRefreshesUpdatedAt() {
            Salary existing = salary(EMPLOYEE_ID, "1000.00");
            ReflectionTestUtils.setField(existing, "updatedAt", LAST_CHANGE);
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(existing));

            SalaryResponse response = salaryService.setSalary(EMPLOYEE_ID, request("1000.01"));

            assertThat(response.updatedAt()).isAfter(LAST_CHANGE);
        }

        @Test
        void sameAmountInADifferentCurrencyIsARealChange() {
            Salary legacy = salary(EMPLOYEE_ID, "1000.00");
            ReflectionTestUtils.setField(legacy, "currency", "EUR");
            ReflectionTestUtils.setField(legacy, "updatedAt", LAST_CHANGE);
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(legacy));

            SalaryResponse response = salaryService.setSalary(EMPLOYEE_ID, request("1000.00"));

            assertThat(response.currency()).isEqualTo("USD");
            assertThat(response.updatedAt()).isAfter(LAST_CHANGE);
        }

        @ParameterizedTest
        @ValueSource(strings = {"1500", "1500.5", "1500.50"})
        void normalizesAmountToTwoDecimalPlaces(String amount) {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(salary(EMPLOYEE_ID, "1.00")));

            BigDecimal stored = salaryService.setSalary(EMPLOYEE_ID, request(amount)).amount();

            assertThat(stored.scale()).isEqualTo(2);
            assertThat(stored).isEqualByComparingTo(amount);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.01", "9999999999999.99"})
        void acceptsBoundaryAmounts(String amount) {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(salary(EMPLOYEE_ID, "1.00")));

            assertThat(salaryService.setSalary(EMPLOYEE_ID, request(amount)).amount())
                    .isEqualTo(new BigDecimal(amount));
        }

        @ParameterizedTest
        @CsvSource({
            "1500.500, 1500.50",
            "1500.5000000, 1500.50",
            "0.010, 0.01",
            "9999999999999.990, 9999999999999.99"})
        void acceptsExtraDecimalPlacesThatAreOnlyTrailingZeros(String amount, String expected) {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(salary(EMPLOYEE_ID, "1.00")));

            assertThat(salaryService.setSalary(EMPLOYEE_ID, request(amount)).amount())
                    .isEqualTo(new BigDecimal(expected));
        }

        @ParameterizedTest
        @CsvSource({
            "1E+2, 100.00",
            "1.5E+3, 1500.00",
            "1E-2, 0.01",
            "9.99999999999999E+12, 9999999999999.99"})
        void acceptsScientificNotationWithinBounds(String amount, String expected) {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(salary(EMPLOYEE_ID, "1.00")));

            assertThat(salaryService.setSalary(EMPLOYEE_ID, request(amount)).amount())
                    .isEqualTo(new BigDecimal(expected));
        }

        @ParameterizedTest
        @ValueSource(strings = {"10.5550", "0.0010", "1E+13", "1E-3", "9999999999999.995", "1E+999999999", "1E-999999999"})
        void rejectsAmountsWhoseSignificantDigitsExceedTheColumn(String amount) {
            assertRejected(new SalaryRequest(new BigDecimal(amount), "USD"), "amount");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "0.00", "-0.01", "-1000.00", "0E-10", "-0.000"})
        void rejectsZeroAndNegativeAmounts(String amount) {
            assertRejected(new SalaryRequest(new BigDecimal(amount), "USD"), "amount");
        }

        @ParameterizedTest
        @ValueSource(strings = {"0.001", "10.555", "10000000000000.00"})
        void rejectsAmountsThatWouldNeedRoundingOrOverflowTheColumn(String amount) {
            assertRejected(new SalaryRequest(new BigDecimal(amount), "USD"), "amount");
        }

        @Test
        void rejectsMissingAmount() {
            assertRejected(new SalaryRequest(null, "USD"), "amount");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "EUR", "usd", "USDX"})
        void rejectsUnsupportedCurrency(String currency) {
            assertRejected(new SalaryRequest(new BigDecimal("100.00"), currency), "currency");
        }

        @Test
        void unknownEmployeeIsReportedAsNotFound() {
            when(salaryRepository.findById(404L)).thenReturn(Optional.empty());
            when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> salaryService.setSalary(404L, request("100.00")))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Employee 404 not found");
            verify(salaryRepository, never()).save(any());
        }

        private void assertRejected(SalaryRequest request, String field) {
            assertThatThrownBy(() -> salaryService.setSalary(EMPLOYEE_ID, request))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining(field);
            verifyNoInteractions(salaryRepository, employeeRepository);
        }
    }

    @Nested
    class GetCurrentSalary {

        @Test
        void returnsCurrentSalary() {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(salary(EMPLOYEE_ID, "4200.00")));

            SalaryResponse response = salaryService.getCurrentSalary(EMPLOYEE_ID);

            assertThat(response.amount()).isEqualTo(new BigDecimal("4200.00"));
            assertThat(response.currency()).isEqualTo("USD");
        }

        @Test
        void employeeWithoutSalaryIsReportedAsNotFound() {
            when(salaryRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.empty());
            when(employeeRepository.existsById(EMPLOYEE_ID)).thenReturn(true);

            assertThatThrownBy(() -> salaryService.getCurrentSalary(EMPLOYEE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Employee 7 has no salary");
        }

        @Test
        void unknownEmployeeIsReportedAsNotFound() {
            when(salaryRepository.findById(404L)).thenReturn(Optional.empty());
            when(employeeRepository.existsById(404L)).thenReturn(false);

            assertThatThrownBy(() -> salaryService.getCurrentSalary(404L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Employee 404 not found");
        }
    }

    @Nested
    class Summarize {

        @Test
        void calculatesCountTotalAndAverage() {
            when(salaryRepository.findAllById(any())).thenReturn(List.of(
                    salary(1L, "100.00"), salary(2L, "200.00"), salary(3L, "300.01")));

            SalarySummary summary = salaryService.summarize(summaryRequest(1L, 2L, 3L));

            assertThat(summary).isEqualTo(new SalarySummary(3, new BigDecimal("600.01"), new BigDecimal("200.00"), "USD"));
        }

        @Test
        void averageUsesBankersRounding() {
            // 0.05 / 2 = 0.025, which HALF_EVEN rounds to 0.02 (HALF_UP would give 0.03)
            when(salaryRepository.findAllById(any())).thenReturn(List.of(salary(1L, "0.01"), salary(2L, "0.04")));

            SalarySummary summary = salaryService.summarize(summaryRequest(1L, 2L));

            assertThat(summary.averageSalary()).isEqualTo(new BigDecimal("0.02"));
        }

        @Test
        void largeTotalsDoNotOverflowOrLosePrecision() {
            when(salaryRepository.findAllById(any())).thenReturn(List.of(
                    salary(1L, "9999999999999.99"), salary(2L, "9999999999999.99"), salary(3L, "0.01")));

            SalarySummary summary = salaryService.summarize(summaryRequest(1L, 2L, 3L));

            assertThat(summary.totalSalary()).isEqualTo(new BigDecimal("19999999999999.99"));
            assertThat(summary.averageSalary()).isEqualTo(new BigDecimal("6666666666666.66"));
        }

        @Test
        void emptyRequestYieldsZeroSummaryWithoutQuerying() {
            SalarySummary summary = salaryService.summarize(new SalarySummaryRequest(List.of()));

            assertThat(summary).isEqualTo(new SalarySummary(0, new BigDecimal("0.00"), new BigDecimal("0.00"), "USD"));
            verifyNoInteractions(salaryRepository);
        }

        @Test
        void employeesWithoutSalaryAreNotCounted() {
            when(salaryRepository.findAllById(any())).thenReturn(List.of());

            SalarySummary summary = salaryService.summarize(summaryRequest(1L, 2L));

            assertThat(summary.employeeCount()).isZero();
            assertThat(summary.totalSalary()).isEqualTo(new BigDecimal("0.00"));
        }

        @Test
        @SuppressWarnings("unchecked")
        void duplicateIdsAreCountedOnce() {
            when(salaryRepository.findAllById(any())).thenReturn(List.of(salary(1L, "100.00")));

            salaryService.summarize(summaryRequest(1L, 1L, 1L));

            ArgumentCaptor<Iterable<Long>> ids = ArgumentCaptor.forClass(Iterable.class);
            verify(salaryRepository).findAllById(ids.capture());
            assertThat(ids.getValue()).containsExactly(1L);
        }

        @Test
        void mixedCurrenciesAreRefusedRatherThanSummed() {
            Salary euro = salary(2L, "100.00");
            ReflectionTestUtils.setField(euro, "currency", "EUR");
            when(salaryRepository.findAllById(any())).thenReturn(List.of(salary(1L, "100.00"), euro));

            assertThatThrownBy(() -> salaryService.summarize(summaryRequest(1L, 2L)))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void rejectsMissingOrNullIds() {
            assertThatThrownBy(() -> salaryService.summarize(new SalarySummaryRequest(null)))
                    .isInstanceOf(ConstraintViolationException.class);
            assertThatThrownBy(() -> salaryService.summarize(new SalarySummaryRequest(Arrays.asList(1L, null))))
                    .isInstanceOf(ConstraintViolationException.class);
            verifyNoInteractions(salaryRepository);
        }

        @Test
        void rejectsTooManyIds() {
            List<Long> tooMany = new ArrayList<>(LongStream.rangeClosed(1, SalarySummaryRequest.MAX_EMPLOYEE_IDS + 1)
                    .boxed().toList());

            assertThatThrownBy(() -> salaryService.summarize(new SalarySummaryRequest(tooMany)))
                    .isInstanceOf(ConstraintViolationException.class)
                    .hasMessageContaining("employeeIds");
            verifyNoInteractions(salaryRepository);
        }

        @Test
        void acceptsMaximumNumberOfIds() {
            List<Long> ids = LongStream.rangeClosed(1, SalarySummaryRequest.MAX_EMPLOYEE_IDS).boxed().toList();
            when(salaryRepository.findAllById(any())).thenReturn(Collections.emptyList());

            assertThat(salaryService.summarize(new SalarySummaryRequest(ids)).employeeCount()).isZero();
        }

        private SalarySummaryRequest summaryRequest(Long... ids) {
            return new SalarySummaryRequest(List.of(ids));
        }
    }

    private static SalaryRequest request(String amount) {
        return new SalaryRequest(new BigDecimal(amount), "USD");
    }

    private static Employee employee(Long id) {
        Employee employee = new Employee("Ada Lovelace", "ada" + id + "@example.com", "Engineering");
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }

    /** A persisted-looking salary: JPA would copy the employee id into the shared key. */
    private static Salary salary(Long employeeId, String amount) {
        Salary salary = new Salary(employee(employeeId), new BigDecimal(amount), "USD");
        ReflectionTestUtils.setField(salary, "employeeId", employeeId);
        return salary;
    }
}
