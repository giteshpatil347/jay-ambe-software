package com.materialbook.employees;

import com.materialbook.audit.service.AuditService;
import com.materialbook.common.ApiException.ConflictException;
import com.materialbook.common.ApiException.NotFoundException;
import com.materialbook.common.ApiException.ValidationException;
import com.materialbook.common.PageResponse;
import com.materialbook.employees.EmployeeDtos.CreateEmployeeRequest;
import com.materialbook.employees.EmployeeDtos.EmployeeResponse;
import com.materialbook.employees.EmployeeDtos.SalaryRevisionResponse;
import com.materialbook.employees.EmployeeDtos.UpdateEmployeeRequest;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Employees and their monthly salary. The source of truth is {@code employee_salary_revisions}: the salary on a date is the
 * latest revision with {@code effective_from <= date}. API reads resolve the salary by query (future-dated revisions therefore
 * apply on their date without a scheduler); {@code employees.monthly_salary} is a mirror of "today's" value, refreshed in the
 * same transaction on every revision write, and only a fallback when no revision is in force yet (e.g. a future joining date).
 * "Today" is the business date in India.
 */
@Service
public class EmployeeService {
    static final String MODULE = "EMPLOYEE";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private final EmployeeRepository repository;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public EmployeeService(EmployeeRepository repository, AuditService audit, JdbcTemplate jdbc) {
        this.repository = repository;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    private record RevisionSnapshot(LocalDate effectiveFrom, BigDecimal monthlySalary, String remarks) {}

    @Transactional(readOnly = true)
    public PageResponse<EmployeeResponse> list(EmployeeStatus status, String q, int page, int size) {
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null) {
                ps.add(cb.equal(root.get("status"), status));
            }
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("employeeName")), like, '\\'),
                        cb.like(cb.lower(root.get("employeeCode")), like, '\\'),
                        cb.like(cb.lower(root.get("mobile")), like, '\\')));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        var result = repository.findAll(spec, PageRequest.of(page, size, Sort.by("employeeName", "id")));
        Map<Long, BigDecimal> current = currentSalaries(result.getContent().stream().map(Employee::getId).toList());
        return PageResponse.of(result, e -> EmployeeResponse.of(e, current.get(e.getId())));
    }

    @Transactional(readOnly = true)
    public EmployeeResponse get(Long id) {
        return response(require(id));
    }

    @Transactional
    public EmployeeResponse create(Long userId, CreateEmployeeRequest r) {
        String code = r.employeeCode() == null || r.employeeCode().isBlank() ? nextCode() : r.employeeCode().trim();
        if (repository.existsByEmployeeCode(code)) {
            throw new ConflictException("Employee code already exists: " + code);
        }
        Employee e = repository.saveAndFlush(new Employee(code, r.employeeName().trim(), r.mobile(), money(r.monthlySalary()),
                r.joiningDate(), EmployeeStatus.ACTIVE));
        LocalDate from = r.effectiveFrom() != null ? r.effectiveFrom()
                : r.joiningDate() != null ? r.joiningDate() : today();
        saveRevision(userId, e, from, money(r.monthlySalary()), null);
        EmployeeResponse snapshot = response(e);
        audit.log("CREATE", MODULE, e.getId(), null, snapshot);
        return snapshot;
    }

    @Transactional
    public EmployeeResponse update(Long userId, Long id, UpdateEmployeeRequest r) {
        Employee e = require(id);
        EmployeeResponse before = response(e);
        e.update(r.employeeName().trim(), r.mobile(), r.joiningDate(), r.status());
        if (r.monthlySalary() != null) {
            LocalDate from = r.effectiveFrom() != null ? r.effectiveFrom() : today();
            BigDecimal salary = money(r.monthlySalary());
            BigDecimal inForce = salaryAsOf(id, from);
            if (inForce == null || inForce.compareTo(salary) != 0) {
                saveRevision(userId, e, from, salary, r.salaryRemarks());
            }
        }
        repository.saveAndFlush(e);
        EmployeeResponse after = response(e);
        audit.log("UPDATE", MODULE, id, before, after);
        return after;
    }

    /** Newest first; {@code year} lets clients group by year. */
    @Transactional(readOnly = true)
    public List<SalaryRevisionResponse> salaryRevisions(Long employeeId) {
        requireExists(employeeId);
        return jdbc.query("""
                select r.id, r.effective_from, r.monthly_salary, r.remarks, u.full_name
                from employee_salary_revisions r left join users u on u.id = r.created_by
                where r.employee_id = ? order by r.effective_from desc""",
                (rs, i) -> {
                    LocalDate from = rs.getObject("effective_from", LocalDate.class);
                    return new SalaryRevisionResponse(rs.getLong("id"), from, from.getYear(), rs.getBigDecimal("monthly_salary"),
                            rs.getString("remarks"), rs.getString("full_name"));
                }, employeeId);
    }

    /** Salary in force on {@code date}; null when no revision is effective yet on that date. */
    @Transactional(readOnly = true)
    public BigDecimal salaryAsOf(Long employeeId, LocalDate date) {
        requireExists(employeeId);
        return jdbc.query("""
                select monthly_salary from employee_salary_revisions
                where employee_id = ? and effective_from <= ? order by effective_from desc limit 1""",
                rs -> rs.next() ? rs.getBigDecimal(1) : null, employeeId, date);
    }

    /** Names of the given employees in one query (used to describe salary rows in expense lists). */
    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> ids) {
        return ids.isEmpty() ? Map.of() : repository.findAllById(ids).stream()
                .collect(Collectors.toMap(Employee::getId, Employee::getEmployeeName));
    }

    /** For the expenses module: the employee a salary payment is made to must exist and be ACTIVE. */
    @Transactional(readOnly = true)
    public void requireActive(Long id) {
        if (require(id).getStatus() != EmployeeStatus.ACTIVE) {
            throw new ValidationException("Employee is inactive and cannot receive salary payments");
        }
    }

    @Transactional(readOnly = true)
    public void requireExists(Long id) {
        require(id);
    }

    private Employee require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Employee not found: " + id));
    }

    private EmployeeResponse response(Employee e) {
        return EmployeeResponse.of(e, currentSalaries(List.of(e.getId())).get(e.getId()));
    }

    /** Inserts the revision, or replaces the one already dated {@code from}; then refreshes the employee mirror. Audited. */
    private void saveRevision(Long userId, Employee employee, LocalDate from, BigDecimal salary, String remarks) {
        Long employeeId = employee.getId();
        String note = remarks == null || remarks.isBlank() ? null : remarks.trim();
        RevisionSnapshot after = new RevisionSnapshot(from, salary, note);
        List<RevisionSnapshot> existing = jdbc.query("""
                select effective_from, monthly_salary, remarks from employee_salary_revisions
                where employee_id = ? and effective_from = ?""",
                (rs, i) -> new RevisionSnapshot(rs.getObject(1, LocalDate.class), rs.getBigDecimal(2), rs.getString(3)),
                employeeId, from);
        if (existing.isEmpty()) {
            jdbc.update("""
                    insert into employee_salary_revisions(employee_id, effective_from, monthly_salary, remarks, created_by)
                    values (?, ?, ?, ?, ?)""", employeeId, from, salary, note, userId);
            audit.log("SALARY_REVISION_ADD", MODULE, employeeId, null, after);
        } else {
            jdbc.update("""
                    update employee_salary_revisions set monthly_salary = ?, remarks = ?, created_by = ?
                    where employee_id = ? and effective_from = ?""", salary, note, userId, employeeId, from);
            audit.log("SALARY_REVISION_UPDATE", MODULE, employeeId, existing.get(0), after);
        }
        BigDecimal current = currentSalaries(List.of(employeeId)).get(employeeId);
        if (current != null) {
            employee.setMonthlySalary(current);
        }
    }

    /** Salary in force today for each id that has a revision effective by today. */
    private Map<Long, BigDecimal> currentSalaries(Collection<Long> ids) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select distinct on (employee_id) employee_id, monthly_salary from employee_salary_revisions
                where employee_id = any(?) and effective_from <= ?
                order by employee_id, effective_from desc""",
                rs -> { result.put(rs.getLong(1), rs.getBigDecimal(2)); }, ids.toArray(Long[]::new), today());
        return result;
    }

    /** Request values have at most 2 decimals (validated); fixing the scale keeps responses identical to later reads. */
    private static BigDecimal money(BigDecimal v) {
        return v.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static LocalDate today() {
        return LocalDate.now(BUSINESS_ZONE);
    }

    private String nextCode() {
        return jdbc.queryForObject("select 'EMP-' || lpad(nextval('employee_code_seq')::text, 4, '0')", String.class);
    }
}
