package io.codzee.reviewlab.salary;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Salaries are keyed by employee id. */
public interface SalaryRepository extends JpaRepository<Salary, Long> {

    /** Aggregate salary figures for one currency. */
    interface CurrencyTotal {

        String getCurrency();

        long getEmployeeCount();

        BigDecimal getTotal();
    }

    /**
     * Counts and sums, per currency, the salaries of the given employees in a single query; ids
     * without a salary contribute nothing. No Salary entities are loaded.
     */
    @Query("""
            select s.currency as currency, count(s) as employeeCount, sum(s.amount) as total
            from Salary s
            where s.employeeId in :employeeIds
            group by s.currency""")
    List<CurrencyTotal> totalsByCurrency(@Param("employeeIds") Collection<Long> employeeIds);
}
