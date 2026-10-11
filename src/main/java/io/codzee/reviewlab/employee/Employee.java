package io.codzee.reviewlab.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "employee")
public class Employee {

    static final int NAME_MAX_LENGTH = 100;
    static final int EMAIL_MAX_LENGTH = 254;
    static final int DEPARTMENT_MAX_LENGTH = 100;

    /** Control characters (Cc) and bidirectional embedding/override/isolate characters. */
    private static final String UNPRINTABLE = "\\p{Cc}\\u202A-\\u202E\\u2066-\\u2069";
    /**
     * Free text such as names: printable characters only, no leading or trailing (Unicode) whitespace.
     * Rejects line breaks that could forge log or CSV lines and bidi overrides that spoof how text displays.
     * Values made only of characters String.trim() removes (U+0000-U+0020) match, so @NotBlank alone
     * reports them instead of two errors per field; other whitespace-only values, such as U+00A0 that
     * {@code @NotBlank} accepts, fail here.
     */
    static final String PRINTABLE_TEXT =
            "(?U)[\\x00-\\x20]*|[^\\s" + UNPRINTABLE + "](?:[^" + UNPRINTABLE + "]*[^\\s" + UNPRINTABLE + "])?";
    static final String PRINTABLE_TEXT_MESSAGE =
            "must not contain control characters or start or end with whitespace";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(nullable = false, length = DEPARTMENT_MAX_LENGTH)
    private String department;

    protected Employee() {
        // required by JPA
    }

    public Employee(String name, String email, String department) {
        this.name = name;
        this.email = email;
        this.department = department;
    }

    void updateDetails(String name, String email, String department) {
        this.name = name;
        this.email = email;
        this.department = department;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getDepartment() {
        return department;
    }
}
