package io.codzee.reviewlab.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.codzee.reviewlab.common.ConflictException;
import io.codzee.reviewlab.common.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

class EmployeeServiceTest {

    private EmployeeRepository employeeRepository;
    private EmployeeService employeeService;

    @BeforeEach
    void setUp() {
        employeeRepository = mock(EmployeeRepository.class);
        employeeService = new EmployeeService(employeeRepository,
                Validation.buildDefaultValidatorFactory().getValidator());
    }

    @Test
    void createsValidEmployeeWithLowerCasedEmail() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(false);
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        EmployeeResponse response = employeeService.create(
                new EmployeeRequest("Ada Lovelace", "Ada@Example.com", "Engineering"));

        assertThat(response).isEqualTo(new EmployeeResponse(1L, "Ada Lovelace", "ada@example.com", "Engineering"));
        ArgumentCaptor<Employee> saved = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ada@example.com");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void rejectsBlankName(String name) {
        assertThatThrownBy(() -> employeeService.create(new EmployeeRequest(name, "ada@example.com", "Engineering")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("name");
        verifyNoInteractions(employeeRepository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-an-email", "ada@", "@example.com", "ada example@example.com"})
    void rejectsInvalidEmail(String email) {
        assertThatThrownBy(() -> employeeService.create(new EmployeeRequest("Ada Lovelace", email, "Engineering")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("email");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void rejectsBlankDepartmentAndOverlongName() {
        String overlongName = "x".repeat(Employee.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> employeeService.create(new EmployeeRequest(overlongName, "ada@example.com", " ")))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("department");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        " Ada Lovelace",           // leading space
        "Ada Lovelace ",           // trailing space
        "Ada Lovelace\u00A0",      // trailing no-break space
        "Ada\tLovelace",           // tab
        "Ada\nLovelace",           // line feed (log / CSV injection)
        "Ada\rLovelace",           // carriage return
        "Ada\0",                   // NUL
        "Ada\u007F",               // DEL
        "\u202Eecalevol adA",      // right-to-left override (display spoofing)
        "Ada\u2066Lovelace",       // left-to-right isolate
        "\u00A0",                  // only a no-break space: String.trim() (used by @NotBlank) keeps it
        "\u2003\u2003"})           // only em spaces
    void rejectsControlCharactersAndSurroundingWhitespace(String text) {
        assertThatThrownBy(() -> employeeService.create(new EmployeeRequest(text, "ada@example.com", text)))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("name")
                .hasMessageContaining("department");
        verifyNoInteractions(employeeRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "A",
        "Ada Lovelace",
        "Jos\u00E9 \u00D1\u00FA\u00F1ez",
        "Se\u00E1n O'Brien-Smith",
        "\u674E\u5C0F\u9F8D",      // Chinese
        "\u0645\u06CC\u200C\u062E\u0648\u0627\u0647\u0645", // Persian; the zero-width non-joiner is a legitimate format character
        "R&D <Platform>"})         // markup is stored verbatim; escaping is the consumer's job
    void acceptsPrintableTextIncludingNonLatinScripts(String text) {
        when(employeeRepository.save(any(Employee.class)))
                .thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));

        EmployeeResponse response = employeeService.create(new EmployeeRequest(text, "ada@example.com", text));

        assertThat(response.name()).isEqualTo(text);
        assertThat(response.department()).isEqualTo(text);
    }

    @Test
    void rejectsDuplicateEmailOnCreateWithoutSaving() {
        when(employeeRepository.existsByEmail("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> employeeService.create(
                new EmployeeRequest("Ada Lovelace", "ADA@example.com", "Engineering")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ada@example.com");
        verify(employeeRepository, never()).save(any());
    }

    @Test
    void getsExistingEmployee() {
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(employee(7L, "ada@example.com")));

        assertThat(employeeService.get(7L).email()).isEqualTo("ada@example.com");
    }

    @Test
    void missingEmployeeIsReportedAsNotFound() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.get(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Employee 404 not found");
    }

    @Test
    void listsEmployeesPageByPage() {
        PageRequest pageable = PageRequest.of(1, 2);
        when(employeeRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(employee(3L, "c@example.com")), pageable, 3));

        Page<EmployeeResponse> page = employeeService.list(pageable);

        assertThat(page.getContent()).extracting(EmployeeResponse::id).containsExactly(3L);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void updatesEmployeeDetails() {
        Employee existing = employee(7L, "ada@example.com");
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(employeeRepository.existsByEmailAndIdNot("countess@example.com", 7L)).thenReturn(false);

        EmployeeResponse response = employeeService.update(7L,
                new EmployeeRequest("Ada King", "Countess@example.com", "Research"));

        assertThat(response).isEqualTo(new EmployeeResponse(7L, "Ada King", "countess@example.com", "Research"));
        assertThat(existing.getDepartment()).isEqualTo("Research");
    }

    @Test
    void rejectsUpdateToAnotherEmployeesEmail() {
        Employee existing = employee(7L, "ada@example.com");
        when(employeeRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(employeeRepository.existsByEmailAndIdNot("alan@example.com", 7L)).thenReturn(true);

        assertThatThrownBy(() -> employeeService.update(7L,
                new EmployeeRequest("Ada Lovelace", "alan@example.com", "Engineering")))
                .isInstanceOf(ConflictException.class);
        assertThat(existing.getEmail()).isEqualTo("ada@example.com");
    }

    @Test
    void updatingMissingEmployeeIsReportedAsNotFound() {
        when(employeeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> employeeService.update(404L,
                new EmployeeRequest("Ada Lovelace", "ada@example.com", "Engineering")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void invalidUpdateIsRejectedBeforeLoadingTheEmployee() {
        assertThatThrownBy(() -> employeeService.update(7L, new EmployeeRequest("", "ada@example.com", "Engineering")))
                .isInstanceOf(ConstraintViolationException.class);
        verifyNoInteractions(employeeRepository);
    }

    private static Employee employee(Long id, String email) {
        return withId(new Employee("Ada Lovelace", email, "Engineering"), id);
    }

    private static Employee withId(Employee employee, Long id) {
        ReflectionTestUtils.setField(employee, "id", id);
        return employee;
    }
}
