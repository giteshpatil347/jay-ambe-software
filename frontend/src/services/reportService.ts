import { api } from '../api/client';
import type {
  ExpenseReportRow, ExpenseSummary, MaterialGroupRow, MaterialRow, MaterialSummary, ExportColumnsKind, ReportFilters, ReportKind, ReportPage, StockRow, StockSummary,
} from '../types/report';
import { downloadFile } from '../utils/download';

const clean = (p: object) => Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== '' && v !== null));
const cols = (c?: string[]) => (c && c.length > 0 ? c : undefined);
type Paged = { page: number; size: number };
const get = <T>(path: string, p: object) => api.get<T>(`/reports/${path}`, { params: clean(p) }).then((r) => r.data);

export const reportService = {
  material: (kind: 'material-in' | 'material-out', f: ReportFilters & Paged) =>
    get<ReportPage<MaterialRow, MaterialSummary>>(kind, f),
  materialGrouped: (kind: 'material-in' | 'material-out', f: ReportFilters & Paged) =>
    get<ReportPage<MaterialGroupRow, MaterialSummary>>(kind, f),
  expenses: (f: ReportFilters & Paged) => get<ReportPage<ExpenseReportRow, ExpenseSummary>>('expenses', f),
  stock: (f: ReportFilters & Paged) => get<ReportPage<StockRow, StockSummary>>('stock', f),
  exportColumns: (kind: ExportColumnsKind) => get<string[]>('export-columns', { kind }),
  /** `columns` = chosen header labels; sent as repeated params only when given (a strict subset), otherwise every column is exported. */
  exportReport: (kind: ReportKind, format: 'pdf' | 'csv', f: ReportFilters, columns?: string[]) =>
    downloadFile(`/reports/${kind}/export`, clean({ ...f, groupBy: undefined, format, columns: cols(columns) }), `${kind}.${format}`),
  ledgerExport: (partyId: number, format: 'pdf' | 'csv', from?: string, to?: string, columns?: string[]) =>
    downloadFile(`/reports/parties/${partyId}/ledger/export`, clean({ format, from, to, columns: cols(columns) }), `ledger.${format}`),
  challanPdf: (id: number, columns?: string[]) =>
    downloadFile(`/reports/transactions/${id}/pdf`, clean({ columns: cols(columns) }), `challan-${id}.pdf`),
};
