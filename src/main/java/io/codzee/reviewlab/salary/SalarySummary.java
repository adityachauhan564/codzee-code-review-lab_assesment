package io.codzee.reviewlab.salary;

import java.math.BigDecimal;

/**
 * Aggregate salary figures. {@code employeeCount} is the number of requested employees that have a
 * salary; totals are exact and the average is rounded to 2 decimals using HALF_EVEN.
 */
public record SalarySummary(int employeeCount, BigDecimal totalSalary, BigDecimal averageSalary, String currency) {
}
