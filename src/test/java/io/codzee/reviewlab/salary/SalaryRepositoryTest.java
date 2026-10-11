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
    void findAllByIdReturnsOnlyEmployeesThatHaveASalary() {
        Employee ada = employeeRepository.save(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));
        Employee alan = employeeRepository.save(new Employee("Alan Turing", "alan@example.com", "Research"));
        salaryRepository.saveAndFlush(new Salary(ada, new BigDecimal("100.00"), "USD"));

        List<Salary> found = salaryRepository.findAllById(List.of(ada.getId(), alan.getId(), UNKNOWN_EMPLOYEE_ID));

        assertThat(found).extracting(Salary::getEmployeeId).containsExactly(ada.getId());
    }
}
