package io.codzee.reviewlab.employee;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for creating or fully updating an employee. */
public record EmployeeRequest(
        @NotBlank @Size(max = Employee.NAME_MAX_LENGTH) String name,
        @NotBlank @Email @Size(max = Employee.EMAIL_MAX_LENGTH) String email,
        @NotBlank @Size(max = Employee.DEPARTMENT_MAX_LENGTH) String department) {
}
