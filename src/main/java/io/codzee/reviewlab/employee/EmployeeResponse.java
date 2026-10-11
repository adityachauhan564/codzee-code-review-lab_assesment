package io.codzee.reviewlab.employee;

/** Public view of an employee. Deliberately carries no salary data. */
public record EmployeeResponse(Long id, String name, String email, String department) {

    static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(employee.getId(), employee.getName(), employee.getEmail(),
                employee.getDepartment());
    }
}
