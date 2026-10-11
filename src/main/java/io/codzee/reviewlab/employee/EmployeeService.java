package io.codzee.reviewlab.employee;

import io.codzee.reviewlab.common.ConflictException;
import io.codzee.reviewlab.common.ResourceNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final Validator validator;

    public EmployeeService(EmployeeRepository employeeRepository, Validator validator) {
        this.employeeRepository = employeeRepository;
        this.validator = validator;
    }

    @Transactional
    public EmployeeResponse create(EmployeeRequest request) {
        validate(request);
        String email = normalizeEmail(request.email());
        if (employeeRepository.existsByEmail(email)) {
            throw duplicateEmail(email);
        }
        Employee employee = new Employee(request.name(), email, request.department());
        return EmployeeResponse.from(employeeRepository.save(employee));
    }

    public EmployeeResponse get(Long id) {
        return EmployeeResponse.from(findEmployee(id));
    }

    public Page<EmployeeResponse> list(Pageable pageable) {
        return employeeRepository.findAll(pageable).map(EmployeeResponse::from);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeRequest request) {
        validate(request);
        Employee employee = findEmployee(id);
        String email = normalizeEmail(request.email());
        if (employeeRepository.existsByEmailAndIdNot(email, id)) {
            throw duplicateEmail(email);
        }
        employee.updateDetails(request.name(), email, request.department());
        return EmployeeResponse.from(employee);
    }

    private Employee findEmployee(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee " + id + " not found"));
    }

    /** Enforces the request's constraints here too, so the rules hold for callers other than the REST layer. */
    private void validate(EmployeeRequest request) {
        Set<ConstraintViolation<EmployeeRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    /** Emails are compared case-insensitively, so they are stored lower-cased. */
    private static String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private static ConflictException duplicateEmail(String email) {
        return new ConflictException("An employee with email " + email + " already exists");
    }
}
