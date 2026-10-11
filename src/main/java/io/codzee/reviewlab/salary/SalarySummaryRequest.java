package io.codzee.reviewlab.salary;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** The employees to summarize. Duplicate ids are counted once; an empty list is allowed. */
public record SalarySummaryRequest(
        @NotNull @Size(max = SalarySummaryRequest.MAX_EMPLOYEE_IDS) List<@NotNull Long> employeeIds) {

    /** Bounds the size of the IN (...) query a single request can trigger. */
    public static final int MAX_EMPLOYEE_IDS = 1000;
}
