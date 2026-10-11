package io.codzee.reviewlab.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void savingAnEmployeeGeneratesAnId() {
        Employee saved = employeeRepository.saveAndFlush(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));

        assertThat(saved.getId()).isNotNull();
        assertThat(employeeRepository.findById(saved.getId()))
                .hasValueSatisfying(found -> assertThat(found.getEmail()).isEqualTo("ada@example.com"));
    }

    @Test
    void databaseRejectsDuplicateEmail() {
        employeeRepository.saveAndFlush(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(
                new Employee("Another Ada", "ada@example.com", "Finance")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailAndIdNotIgnoresTheEmployeeItself() {
        Employee ada = employeeRepository.saveAndFlush(new Employee("Ada Lovelace", "ada@example.com", "Engineering"));
        Employee alan = employeeRepository.saveAndFlush(new Employee("Alan Turing", "alan@example.com", "Research"));

        assertThat(employeeRepository.existsByEmail("ada@example.com")).isTrue();
        assertThat(employeeRepository.existsByEmail("nobody@example.com")).isFalse();
        assertThat(employeeRepository.existsByEmailAndIdNot("ada@example.com", ada.getId())).isFalse();
        assertThat(employeeRepository.existsByEmailAndIdNot("ada@example.com", alan.getId())).isTrue();
    }
}
