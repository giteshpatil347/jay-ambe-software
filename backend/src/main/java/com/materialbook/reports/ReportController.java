package com.materialbook.reports;

import com.materialbook.reports.ReportDtos.Dashboard;
import com.materialbook.reports.ReportDtos.ExpenseRow;
import com.materialbook.reports.ReportDtos.ExpenseSummary;
import com.materialbook.reports.ReportDtos.MaterialSummary;
import com.materialbook.reports.ReportDtos.ReportPage;
import com.materialbook.reports.ReportDtos.StockReportRow;
import com.materialbook.reports.ReportDtos.StockSummary;
import com.materialbook.reports.ReportQueryService.GroupBy;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reports (JSON), dashboard and PDF/CSV exports. Exports need REPORT.EXPORT plus the module's own export permission. */
@RestController
@RequestMapping("/api/v1/reports")
@Validated
public class ReportController {
    private final ReportQueryService queries;
    private final DashboardService dashboards;
    private final ReportExportService exports;

    public ReportController(ReportQueryService queries, DashboardService dashboards, ReportExportService exports) {
        this.queries = queries;
        this.dashboards = dashboards;
        this.exports = exports;
    }

    @GetMapping("/material-in")
    @PreAuthorize("hasAuthority('REPORT.VIEW')")
    ReportPage<?, MaterialSummary> materialIn(ReportFilter filter, @RequestParam(required = false) String groupBy,
                                              @RequestParam(defaultValue = "0") @Min(0) int page,
                                              @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return material(MaterialType.IN, filter, groupBy, page, size);
    }

    @GetMapping("/material-out")
    @PreAuthorize("hasAuthority('REPORT.VIEW')")
    ReportPage<?, MaterialSummary> materialOut(ReportFilter filter, @RequestParam(required = false) String groupBy,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return material(MaterialType.OUT, filter, groupBy, page, size);
    }

    private ReportPage<?, MaterialSummary> material(MaterialType type, ReportFilter filter, String groupBy, int page, int size) {
        return groupBy == null || groupBy.isBlank()
                ? queries.materialReport(type, filter, page, size)
                : queries.materialGrouped(type, filter, GroupBy.parse(groupBy), page, size);
    }

    @GetMapping("/expenses")
    @PreAuthorize("hasAuthority('REPORT.VIEW')")
    ReportPage<ExpenseRow, ExpenseSummary> expenses(ReportFilter filter, @RequestParam(defaultValue = "0") @Min(0) int page,
                                                    @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return queries.expenseReport(filter, page, size);
    }

    @GetMapping("/stock")
    @PreAuthorize("hasAuthority('REPORT.VIEW')")
    ReportPage<StockReportRow, StockSummary> stock(StockFilter filter, @RequestParam(defaultValue = "0") @Min(0) int page,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return queries.stockReport(filter, page, size);
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('DASHBOARD.VIEW')")
    Dashboard dashboard(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dashboards.dashboard(LocalDate.now(ReportTable.IST), from, to);
    }

    /** Ordered column labels of an export kind; the same definitions the exporters use. Challan users may not hold REPORT.EXPORT. */
    @GetMapping("/export-columns")
    @PreAuthorize("hasAnyAuthority('REPORT.EXPORT', 'MATERIAL_IN.EXPORT', 'MATERIAL_OUT.EXPORT')")
    List<String> exportColumns(@RequestParam String kind) {
        return ReportColumns.labels(kind);
    }

    @GetMapping("/material-in/export")
    @PreAuthorize("hasAuthority('REPORT.EXPORT') and hasAuthority('MATERIAL_IN.EXPORT')")
    void exportMaterialIn(ReportFilter filter, @RequestParam String format, @RequestParam(required = false) List<String> columns,
                    HttpServletResponse response) throws IOException {
        exports.material(MaterialType.IN, filter, columns, format, response);
    }

    @GetMapping("/material-out/export")
    @PreAuthorize("hasAuthority('REPORT.EXPORT') and hasAuthority('MATERIAL_OUT.EXPORT')")
    void exportMaterialOut(ReportFilter filter, @RequestParam String format, @RequestParam(required = false) List<String> columns,
                    HttpServletResponse response) throws IOException {
        exports.material(MaterialType.OUT, filter, columns, format, response);
    }

    @GetMapping("/expenses/export")
    @PreAuthorize("hasAuthority('REPORT.EXPORT') and hasAuthority('EXPENSE.EXPORT')")
    void exportExpenses(ReportFilter filter, @RequestParam String format, @RequestParam(required = false) List<String> columns,
                    HttpServletResponse response) throws IOException {
        exports.expenses(filter, columns, format, response);
    }

    @GetMapping("/stock/export")
    @PreAuthorize("hasAuthority('REPORT.EXPORT') and hasAuthority('INVENTORY.EXPORT')")
    void exportStock(StockFilter filter, @RequestParam String format, @RequestParam(required = false) List<String> columns,
                    HttpServletResponse response) throws IOException {
        exports.stock(filter, columns, format, response);
    }

    @GetMapping("/parties/{id}/ledger/export")
    @PreAuthorize("hasAuthority('REPORT.EXPORT') and hasAuthority('PARTY.VIEW')")
    void exportLedger(@PathVariable Long id, @RequestParam String format,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(required = false) List<String> columns, HttpServletResponse response) throws IOException {
        exports.ledger(id, from, to, columns, format, response);
    }

    @GetMapping("/transactions/{id}/pdf")
    @PreAuthorize("hasAnyAuthority('MATERIAL_IN.EXPORT', 'MATERIAL_OUT.EXPORT')")
    void transactionPdf(@PathVariable Long id, @RequestParam(required = false) List<String> columns, Authentication authentication, HttpServletResponse response) throws IOException {
        exports.challan(id, columns, authentication, response);
    }
}
