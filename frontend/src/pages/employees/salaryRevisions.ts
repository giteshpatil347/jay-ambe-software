import type { Employee, EmployeeBody, SalaryRevision } from '../../types/expense';
import { subtractMoney } from '../../utils/money';

export interface RevisionRow extends SalaryRevision {
  /** Signed difference to the previous (older) revision, "+2000.00" / "-500.00"; null for the first revision. */
  change: string | null;
}
export interface YearGroup { year: number; rows: RevisionRow[] }

/** Groups revisions (any order) by calendar year, newest year first and newest revision first inside a year. */
export function groupRevisionsByYear(revisions: SalaryRevision[]): YearGroup[] {
  const sorted = [...revisions].sort((a, b) => b.effectiveFrom.localeCompare(a.effectiveFrom));
  const groups: YearGroup[] = [];
  sorted.forEach((r, i) => {
    const older = sorted[i + 1];
    const diff = older ? subtractMoney(r.monthlySalary, older.monthlySalary) : null;
    const row: RevisionRow = { ...r, change: diff === null ? null : diff.startsWith('-') ? diff : `+${diff}` };
    const last = groups[groups.length - 1];
    if (last && last.year === r.year) last.rows.push(row);
    else groups.push({ year: r.year, rows: [row] });
  });
  return groups;
}

/** PUT body for "Add increment": the employee's own fields unchanged plus the new salary with its effective date. */
export function buildIncrementBody(e: Employee, v: { monthlySalary: string; effectiveFrom: string; remarks?: string }): EmployeeBody {
  return {
    employeeName: e.employeeName, mobile: e.mobile ?? undefined, joiningDate: e.joiningDate ?? undefined, status: e.status,
    monthlySalary: v.monthlySalary.trim(), effectiveFrom: v.effectiveFrom, salaryRemarks: v.remarks?.trim() || undefined,
  };
}
