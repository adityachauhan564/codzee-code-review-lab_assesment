package io.codzee.reviewlab.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.codzee.reviewlab.employee.Employee;
import io.codzee.reviewlab.employee.EmployeeRepository;
import io.codzee.reviewlab.salary.Salary;
import io.codzee.reviewlab.salary.SalaryRepository;
import io.codzee.reviewlab.salary.SalaryService;
import io.codzee.reviewlab.salary.SalarySummary;
import io.codzee.reviewlab.salary.SalarySummaryRequest;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Guards the cost of a salary summary: it must be answered by one aggregate query, without loading
 * a Salary entity per employee, even for the largest request the API accepts.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SalarySummaryQueryTest {

    @Autowired
    private SalaryService salaryService;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SalaryRepository salaryRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void summaryOfTheLargestAllowedRequestRunsOneQueryAndLoadsNoEntities() {
        int employees = SalarySummaryRequest.MAX_EMPLOYEE_IDS;
        List<Long> employeeIds = seedEmployeesWithSalaries(employees);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        SalarySummary summary = salaryService.summarize(new SalarySummaryRequest(employeeIds));

        // Salaries are 1.00 .. 1000.00: total 500500.00, average 500.50.
        assertThat(summary).isEqualTo(
                new SalarySummary(employees, new BigDecimal("500500.00"), new BigDecimal("500.50"), "USD"));
        assertThat(statistics.getEntityLoadCount()).as("entities loaded").isZero();
        assertThat(statistics.getPrepareStatementCount()).as("SQL statements").isEqualTo(1);
    }

    /** Seeds in one transaction so each Salary can share the key of its still-managed Employee. */
    private List<Long> seedEmployeesWithSalaries(int count) {
        String run = UUID.randomUUID().toString();
        return transactionTemplate.execute(status -> {
            List<Long> ids = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                Employee employee = employeeRepository.save(
                        new Employee("Employee " + i, "e" + i + "-" + run + "@summary.test", "Engineering"));
                salaryRepository.save(new Salary(employee, BigDecimal.valueOf(i).setScale(2), "USD"));
                ids.add(employee.getId());
            }
            return ids;
        });
    }
}
