import { DatePicker, Flex, Select, Switch } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import type { ReportFilters, ReportStatus } from '../../types/report';
import { toIsoDate } from '../../utils/date';

export const STATUS_OPTIONS = [{ value: 'POSTED', label: 'Posted' }, { value: 'CANCELLED', label: 'Cancelled' }];

export function DateRangeFilter({ f, onChange }: { f: ReportFilters; onChange: (p: Partial<ReportFilters>) => void }) {
  const value: [Dayjs | null, Dayjs | null] = [f.from ? dayjs(f.from) : null, f.to ? dayjs(f.to) : null];
  return (
    <DatePicker.RangePicker value={value} allowEmpty={[true, true]} style={{ minHeight: 40, maxWidth: '100%' }}
      onChange={(v) => onChange({ from: v?.[0] ? toIsoDate(v[0]) : undefined, to: v?.[1] ? toIsoDate(v[1]) : undefined })} />
  );
}

export function StatusFilter({ f, onChange }: { f: ReportFilters; onChange: (p: Partial<ReportFilters>) => void }) {
  return <Select allowClear placeholder="Status" aria-label="Status filter" style={{ minWidth: 130 }} value={f.status}
    options={STATUS_OPTIONS} onChange={(v?: ReportStatus) => onChange({ status: v })} />;
}

export function AsOfFilter({ f, onChange }: { f: ReportFilters; onChange: (p: Partial<ReportFilters>) => void }) {
  return <DatePicker aria-label="As of date" placeholder="As of (today)" value={f.asOf ? dayjs(f.asOf) : null} style={{ minHeight: 40 }}
    onChange={(v) => onChange({ asOf: v ? toIsoDate(v) : undefined })} />;
}

export function LowStockSwitch({ f, onChange }: { f: ReportFilters; onChange: (p: Partial<ReportFilters>) => void }) {
  return (
    <Flex align="center" gap={8} style={{ minHeight: 40 }}>
      <Switch checked={!!f.lowStockOnly} onChange={(v) => onChange({ lowStockOnly: v || undefined })} aria-label="Low stock only" />
      <span>Low stock only</span>
    </Flex>
  );
}
