package io.codzee.reviewlab.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.codzee.reviewlab.employee.Employee;
import io.codzee.reviewlab.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SalaryRepositoryTest {

    /** Never assigned: the test database is shared across contexts, so a small "missing" id may exist. */
    private static final long UNKNOWN_EMPLOYEE_ID = Long.MAX_VALUE;

    @Autowired
    private SalaryRepository salaryRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void salaryIsKeyedByItsEmployeeAndStoresTwoDecimalPlaces() {
        Employee ada = employeeRepository.save(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));
        salaryRepository.saveAndFlush(new Salary(ada, new BigDecimal("1234.50"), "USD"));
        entityManager.clear();

        Salary found = salaryRepository.findById(ada.getId()).orElseThrow();

        assertThat(found.getEmployeeId()).isEqualTo(ada.getId());
        assertThat(found.getAmount()).isEqualTo(new BigDecimal("1234.50"));
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.getUpdatedAt()).isNotNull();
    }

    @Test
    void databaseRejectsNonPositiveAmount() {
        Employee ada = employeeRepository.save(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));

        assertThatThrownBy(() -> salaryRepository.saveAndFlush(new Salary(ada, BigDecimal.ZERO, "USD")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void totalsByCurrencyCountsAndSumsExactlyInTheDatabase() {
        Employee ada = employeeRepository.save(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));
        Employee alan = employeeRepository.save(new Employee("Alan Turing", "alan@example.com", "Research"));
        Employee grace = employeeRepository.save(new Employee("Grace Hopper", "grace@example.com", "Navy"));
        Employee unpaid = employeeRepository.save(new Employee("No Salary", "none@example.com", "Ops"));
        salaryRepository.save(new Salary(ada, new BigDecimal("9999999999999.99"), "USD"));
        salaryRepository.save(new Salary(alan, new BigDecimal("9999999999999.99"), "USD"));
        salaryRepository.save(new Salary(grace, new BigDecimal("0.01"), "USD"));
        entityManager.flush();
        entityManager.clear();

        List<SalaryRepository.CurrencyTotal> totals = salaryRepository.totalsByCurrency(
                List.of(ada.getId(), alan.getId(), grace.getId(), unpaid.getId(), UNKNOWN_EMPLOYEE_ID));

        assertThat(totals).singleElement().satisfies(total -> {
            assertThat(total.getCurrency()).isEqualTo("USD");
            assertThat(total.getEmployeeCount()).isEqualTo(3);
            // Exceeds DECIMAL(15,2): the database must not truncate or round the sum.
            assertThat(total.getTotal()).isEqualByComparingTo("19999999999999.99");
        });
    }

    @Test
    void totalsByCurrencyReturnsOneRowPerCurrency() {
        Employee ada = employeeRepository.save(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));
        Employee alan = employeeRepository.save(new Employee("Alan Turing", "alan@example.com", "Research"));
        salaryRepository.save(new Salary(ada, new BigDecimal("100.00"), "USD"));
        salaryRepository.save(new Salary(alan, new BigDecimal("50.00"), "EUR"));
        entityManager.flush();

        assertThat(salaryRepository.totalsByCurrency(List.of(ada.getId(), alan.getId())))
                .extracting(SalaryRepository.CurrencyTotal::getCurrency)
                .containsExactlyInAnyOrder("USD", "EUR");
    }

    @Test
    void totalsByCurrencyIsEmptyWhenNoRequestedEmployeeHasASalary() {
        Employee unpaid = employeeRepository.save(new Employee("No Salary", "none@example.com", "Ops"));

        assertThat(salaryRepository.totalsByCurrency(List.of(unpaid.getId(), UNKNOWN_EMPLOYEE_ID))).isEmpty();
    }
}
