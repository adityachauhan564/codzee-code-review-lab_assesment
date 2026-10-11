package io.codzee.reviewlab.salary;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class SalaryController {

    private final SalaryService salaryService;

    public SalaryController(SalaryService salaryService) {
        this.salaryService = salaryService;
    }

    @GetMapping("/employees/{employeeId}/salary")
    public SalaryResponse getCurrentSalary(@PathVariable Long employeeId) {
        return salaryService.getCurrentSalary(employeeId);
    }

    /** Idempotent: creates the current salary or replaces it. */
    @PutMapping("/employees/{employeeId}/salary")
    public SalaryResponse setSalary(@PathVariable Long employeeId, @Valid @RequestBody SalaryRequest request) {
        return salaryService.setSalary(employeeId, request);
    }

    /** A read-only calculation; POST is used so the id list travels in a JSON body. */
    @PostMapping("/salaries/summary")
    public SalarySummary summarize(@Valid @RequestBody SalarySummaryRequest request) {
        return salaryService.summarize(request);
    }
}
