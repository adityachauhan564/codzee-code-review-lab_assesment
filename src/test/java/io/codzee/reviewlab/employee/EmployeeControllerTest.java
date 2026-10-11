package io.codzee.reviewlab.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.codzee.reviewlab.common.ConflictException;
import io.codzee.reviewlab.common.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmployeeController.class)
@ExtendWith(OutputCaptureExtension.class)
class EmployeeControllerTest {

    private static final String VALID_BODY = """
            {"name": "Ada Lovelace", "email": "ada@example.com", "department": "Engineering"}
            """;
    private static final EmployeeResponse ADA = new EmployeeResponse(1L, "Ada Lovelace", "ada@example.com", "Engineering");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        when(employeeService.create(any())).thenReturn(ADA);

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/employees/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("ada@example.com"));
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        String body = """
                {"name": " ", "email": "not-an-email", "department": "Engineering"}
                """;

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("name", "email")));
        verifyNoInteractions(employeeService);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(employeeService);
    }

    @Test
    void serviceLevelValidationFailureReturns400WithFieldErrors() throws Exception {
        var violations = Validation.buildDefaultValidatorFactory().getValidator()
                .validate(new EmployeeRequest("", "ada@example.com", "Engineering"));
        when(employeeService.create(any())).thenThrow(new ConstraintViolationException(violations));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(employeeService.create(any()))
                .thenThrow(new ConflictException("An employee with email ada@example.com already exists"));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An employee with email ada@example.com already exists"));
    }

    @Test
    void databaseConstraintViolationReturns409WithoutSqlDetailsOrPersonalDataInLogs(CapturedOutput output)
            throws Exception {
        when(employeeService.create(any())).thenThrow(new DataIntegrityViolationException(
                "could not execute statement [Duplicate entry 'ada@example.com' for key 'employee.uk_employee_email']"));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data"))
                .andExpect(content().string(not(containsString("uk_employee_email"))));
        assertThat(output).contains("Data integrity violation").doesNotContain("ada@example.com");
    }

    @Test
    void getReturnsEmployee() throws Exception {
        when(employeeService.get(1L)).thenReturn(ADA);

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada Lovelace"))
                .andExpect(jsonPath("$.salary").doesNotExist());
    }

    @Test
    void missingEmployeeReturns404Problem() throws Exception {
        when(employeeService.get(404L)).thenThrow(new ResourceNotFoundException("Employee 404 not found"));

        mockMvc.perform(get("/api/employees/404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Employee 404 not found"))
                .andExpect(jsonPath("$.instance").value("/api/employees/404"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mockMvc.perform(get("/api/employees/abc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(employeeService);
    }

    @Test
    void listReturnsPageWithDefaultSizeAndSortById() throws Exception {
        Pageable expected = PageRequest.of(0, 20, Sort.by("id"));
        when(employeeService.list(eq(expected))).thenReturn(new PageImpl<>(List.of(ADA), expected, 1));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void listCapsPageSize() throws Exception {
        Pageable capped = PageRequest.of(0, 100, Sort.by("id"));
        when(employeeService.list(eq(capped))).thenReturn(new PageImpl<>(List.of(), capped, 0));

        mockMvc.perform(get("/api/employees").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(100));
    }

    @Test
    void unknownSortPropertyReturns400() throws Exception {
        when(employeeService.list(any())).thenThrow(
                new PropertyReferenceException("salary", TypeInformation.of(Employee.class), List.of()));

        mockMvc.perform(get("/api/employees").param("sort", "salary"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown sort property: salary"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(employeeService.update(eq(1L), any())).thenReturn(ADA);

        mockMvc.perform(put("/api/employees/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void unexpectedErrorReturns500WithoutInternals() throws Exception {
        when(employeeService.get(1L)).thenThrow(
                new IllegalStateException("SQL [select * from employee] failed: password=hunter2"));

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(content().string(not(containsString("SQL"))))
                .andExpect(content().string(not(containsString("hunter2"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
