package io.codzee.reviewlab.salary;

import io.codzee.reviewlab.common.ResourceNotFoundException;
import io.codzee.reviewlab.employee.Employee;
import io.codzee.reviewlab.employee.EmployeeRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SalaryService {

    private static final RoundingMode AVERAGE_ROUNDING = RoundingMode.HALF_EVEN;
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(Salary.AMOUNT_SCALE);

    private final SalaryRepository salaryRepository;
    private final EmployeeRepository employeeRepository;
    private final Validator validator;

    public SalaryService(SalaryRepository salaryRepository, EmployeeRepository employeeRepository,
            Validator validator) {
        this.salaryRepository = salaryRepository;
        this.employeeRepository = employeeRepository;
        this.validator = validator;
    }

    public SalaryResponse getCurrentSalary(Long employeeId) {
        return salaryRepository.findById(employeeId)
                .map(SalaryResponse::from)
                .orElseThrow(() -> employeeRepository.existsById(employeeId)
                        ? new ResourceNotFoundException("Employee " + employeeId + " has no salary")
                        : employeeNotFound(employeeId));
    }

    /** Sets the employee's current salary, creating it on first use and replacing it afterwards. */
    @Transactional
    public SalaryResponse setSalary(Long employeeId, SalaryRequest request) {
        validate(request);
        // Exact: validation guarantees at most AMOUNT_SCALE decimals, so no rounding can happen here.
        BigDecimal amount = request.amount().setScale(Salary.AMOUNT_SCALE);
        Salary salary = salaryRepository.findById(employeeId).orElse(null);
        if (salary != null) {
            salary.change(amount, request.currency());
            return SalaryResponse.from(salary);
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> employeeNotFound(employeeId));
        return SalaryResponse.from(salaryRepository.save(new Salary(employee, amount, request.currency())));
    }

    public SalarySummary summarize(SalarySummaryRequest request) {
        validate(request);
        Set<Long> employeeIds = new LinkedHashSet<>(request.employeeIds());
        if (employeeIds.isEmpty()) {
            return calculateSummary(List.of());
        }
        return calculateSummary(salaryRepository.findAllById(employeeIds));
    }

    private static SalarySummary calculateSummary(List<Salary> salaries) {
        if (salaries.stream().anyMatch(salary -> !Salary.SUPPORTED_CURRENCY.equals(salary.getCurrency()))) {
            throw new IllegalStateException("Cannot summarize salaries in different currencies");
        }
        BigDecimal total = salaries.stream()
                .map(Salary::getAmount)
                .reduce(ZERO_AMOUNT, BigDecimal::add);
        BigDecimal average = salaries.isEmpty()
                ? ZERO_AMOUNT
                : total.divide(BigDecimal.valueOf(salaries.size()), Salary.AMOUNT_SCALE, AVERAGE_ROUNDING);
        return new SalarySummary(salaries.size(), total, average, Salary.SUPPORTED_CURRENCY);
    }

    private <T> void validate(T request) {
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private static ResourceNotFoundException employeeNotFound(Long employeeId) {
        return new ResourceNotFoundException("Employee " + employeeId + " not found");
    }
}
