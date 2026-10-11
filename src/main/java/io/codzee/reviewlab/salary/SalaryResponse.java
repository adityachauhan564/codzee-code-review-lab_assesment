package io.codzee.reviewlab.salary;

import java.math.BigDecimal;
import java.time.Instant;

public record SalaryResponse(Long employeeId, BigDecimal amount, String currency, Instant updatedAt) {

    static SalaryResponse from(Salary salary) {
        return new SalaryResponse(salary.getEmployeeId(), salary.getAmount(), salary.getCurrency(),
                salary.getUpdatedAt());
    }
}
