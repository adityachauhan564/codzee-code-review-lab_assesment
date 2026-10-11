package io.codzee.reviewlab.salary;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codzee.reviewlab.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SalaryController.class)
class SalaryControllerTest {

    private static final SalaryResponse SALARY = new SalaryResponse(
            7L, new BigDecimal("1234.50"), "USD", Instant.parse("2026-01-15T10:00:00Z"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SalaryService salaryService;

    @Test
    void putSetsSalaryAndReturns200WithExactAmount() throws Exception {
        when(salaryService.setSalary(eq(7L), any())).thenReturn(SALARY);

        mockMvc.perform(put("/api/employees/7/salary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1234.50, \"currency\": \"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(7))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.updatedAt").value("2026-01-15T10:00:00Z"))
                .andExpect(content().string(containsString("\"amount\":1234.50")));
        verify(salaryService).setSalary(7L, new SalaryRequest(new BigDecimal("1234.50"), "USD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1234.500", "1.2345E+3", "1234.5"})
    void equivalentAmountNotationsReachTheServiceAsTheSameValue(String amount) throws Exception {
        when(salaryService.setSalary(eq(7L), any())).thenReturn(SALARY);

        mockMvc.perform(put("/api/employees/7/salary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + ", \"currency\": \"USD\"}"))
                .andExpect(status().isOk());
        verify(salaryService).setSalary(7L, new SalaryRequest(new BigDecimal("1234.50"), "USD"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5.00", "10.555", "\"abc\"", "1E+999999999"})
    void invalidAmountReturns400(String amount) throws Exception {
        mockMvc.perform(put("/api/employees/7/salary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": " + amount + ", \"currency\": \"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(salaryService);
    }

    @Test
    void unsupportedCurrencyReturns400WithFieldError() throws Exception {
        mockMvc.perform(put("/api/employees/7/salary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"currency\": \"EUR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"))
                .andExpect(jsonPath("$.errors[0].message").value("must be USD, the only supported currency"));
        verifyNoInteractions(salaryService);
    }

    @Test
    void settingSalaryOfUnknownEmployeeReturns404() throws Exception {
        when(salaryService.setSalary(eq(404L), any()))
                .thenThrow(new ResourceNotFoundException("Employee 404 not found"));

        mockMvc.perform(put("/api/employees/404/salary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100.00, \"currency\": \"USD\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee 404 not found"));
    }

    @Test
    void getReturnsCurrentSalary() throws Exception {
        when(salaryService.getCurrentSalary(7L)).thenReturn(SALARY);

        mockMvc.perform(get("/api/employees/7/salary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(7));
    }

    @Test
    void getWithoutSalaryReturns404() throws Exception {
        when(salaryService.getCurrentSalary(7L)).thenThrow(new ResourceNotFoundException("Employee 7 has no salary"));

        mockMvc.perform(get("/api/employees/7/salary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee 7 has no salary"));
    }

    @Test
    void summaryReturns200() throws Exception {
        when(salaryService.summarize(new SalarySummaryRequest(List.of(1L, 2L)))).thenReturn(
                new SalarySummary(2, new BigDecimal("300.00"), new BigDecimal("150.00"), "USD"));

        mockMvc.perform(post("/api/salaries/summary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\": [1, 2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCount").value(2))
                .andExpect(content().string(containsString("\"totalSalary\":300.00")))
                .andExpect(content().string(containsString("\"averageSalary\":150.00")));
    }

    @Test
    void summaryWithoutIdsReturns400() throws Exception {
        mockMvc.perform(post("/api/salaries/summary").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("employeeIds"));
        verifyNoInteractions(salaryService);
    }
}
