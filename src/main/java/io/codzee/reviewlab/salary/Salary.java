package io.codzee.reviewlab.salary;

import io.codzee.reviewlab.employee.Employee;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * An employee's current salary. It shares the employee's primary key, so each employee has at most
 * one salary; replacing it overwrites the row (no history is kept).
 */
@Entity
@Table(name = "salary")
public class Salary {

    /** The single currency the system accepts; amounts in different currencies are never mixed. */
    public static final String SUPPORTED_CURRENCY = "USD";

    /** Monetary amounts are stored as DECIMAL(15,2): up to 13 integer digits and exactly 2 decimals. */
    static final int AMOUNT_INTEGER_DIGITS = 13;
    static final int AMOUNT_SCALE = 2;
    /** Largest value DECIMAL(15,2) can hold. */
    static final String MAX_AMOUNT = "9999999999999.99";

    @Id
    @Column(name = "employee_id")
    private Long employeeId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(nullable = false, precision = AMOUNT_INTEGER_DIGITS + AMOUNT_SCALE, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Salary() {
        // required by JPA
    }

    public Salary(Employee employee, BigDecimal amount, String currency) {
        this.employee = employee;
        this.amount = amount;
        this.currency = currency;
        this.updatedAt = now();
    }

    /** Replaces the salary; re-setting the current value is a no-op, so updatedAt only moves on real changes. */
    void change(BigDecimal amount, String currency) {
        if (this.amount.compareTo(amount) == 0 && this.currency.equals(currency)) {
            return;
        }
        this.amount = amount;
        this.currency = currency;
        this.updatedAt = now();
    }

    /** Truncated to the DATETIME(6) column precision so API responses match what is stored. */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
