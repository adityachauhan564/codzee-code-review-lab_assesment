package io.codzee.reviewlab.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.codzee.reviewlab.salary.SalaryRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Full stack: HTTP -> controllers -> services -> JPA -> Flyway-managed H2 schema. Every test uses
 * unique emails because the in-memory database is shared with other test contexts in the JVM.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeSalaryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SalaryRepository salaryRepository;

    @Test
    void employeeAndSalaryLifecycle() throws Exception {
        long ada = createEmployee("Ada Lovelace", uniqueEmail("ada"));
        long alan = createEmployee("Alan Turing", uniqueEmail("alan"));

        mockMvc.perform(get("/api/employees/{id}/salary", ada))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee " + ada + " has no salary"));

        setSalary(ada, "1000.10").andExpect(status().isOk());
        setSalary(ada, "1500.5").andExpect(status().isOk())
                .andExpect(content().string(containsString("\"amount\":1500.50")));
        setSalary(alan, "2000.00").andExpect(status().isOk());

        assertThat(salaryRepository.findById(ada).orElseThrow().getAmount()).isEqualTo(new BigDecimal("1500.50"));

        mockMvc.perform(get("/api/employees/{id}", ada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").doesNotExist())
                .andExpect(jsonPath("$.salary").doesNotExist());

        mockMvc.perform(post("/api/salaries/summary").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\": [" + ada + ", " + alan + ", " + ada + ", 999999]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCount").value(2))
                .andExpect(content().string(containsString("\"totalSalary\":3500.50")))
                .andExpect(content().string(containsString("\"averageSalary\":1750.25")))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void duplicateEmailIsRejectedCaseInsensitively() throws Exception {
        String email = uniqueEmail("grace");
        createEmployee("Grace Hopper", email);

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("Another Grace", email.toUpperCase())))
                .andExpect(status().isConflict());
    }

    @Test
    void updateChangesDetailsAndKeepsEmailsUnique() throws Exception {
        long grace = createEmployee("Grace Hopper", uniqueEmail("grace"));
        String takenEmail = uniqueEmail("taken");
        createEmployee("Someone Else", takenEmail);

        mockMvc.perform(put("/api/employees/{id}", grace).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("Rear Admiral Hopper", uniqueEmail("hopper"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rear Admiral Hopper"));

        mockMvc.perform(put("/api/employees/{id}", grace).contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson("Rear Admiral Hopper", takenEmail)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidAndMissingResourcesAreReportedConsistently() throws Exception {
        setSalary(999999, "100.00").andExpect(status().isNotFound());
        setSalary(999999, "0").andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/employees/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employees").param("sort", "salary")).andExpect(status().isBadRequest());
    }

    @Test
    void listIsPaginated() throws Exception {
        createEmployee("Page One", uniqueEmail("p1"));
        createEmployee("Page Two", uniqueEmail("p2"));

        mockMvc.perform(get("/api/employees").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page.size").value(1));
    }

    private long createEmployee(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content(employeeJson(name, email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions setSalary(long employeeId, String amount)
            throws Exception {
        return mockMvc.perform(put("/api/employees/{id}/salary", employeeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": " + amount + ", \"currency\": \"USD\"}"));
    }

    private static String employeeJson(String name, String email) {
        return "{\"name\": \"%s\", \"email\": \"%s\", \"department\": \"Engineering\"}".formatted(name, email);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@integration.test";
    }
}
