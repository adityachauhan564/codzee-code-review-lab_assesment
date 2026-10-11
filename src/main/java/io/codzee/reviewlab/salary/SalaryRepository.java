package io.codzee.reviewlab.salary;

import org.springframework.data.jpa.repository.JpaRepository;

/** Salaries are keyed by employee id. */
public interface SalaryRepository extends JpaRepository<Salary, Long> {
}
