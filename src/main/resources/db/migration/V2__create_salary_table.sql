-- One row per employee: the employee's current salary. The primary key is the
-- employee id, so an employee can never have two "current" salaries.
CREATE TABLE salary (
    employee_id BIGINT        NOT NULL,
    amount      DECIMAL(15,2) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT pk_salary PRIMARY KEY (employee_id),
    CONSTRAINT fk_salary_employee FOREIGN KEY (employee_id) REFERENCES employee (id),
    CONSTRAINT ck_salary_amount_positive CHECK (amount > 0)
);
