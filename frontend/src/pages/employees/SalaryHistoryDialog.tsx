import { useMemo, useState } from 'react';
import { Alert, Button, DatePicker, Drawer, Form, Input, Spin, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormDialog } from '../../components/common/FormDialog';
import { employeeService } from '../../services/employeeService';
import type { Employee } from '../../types/expense';
import { formatCurrency } from '../../utils/currency';
import { formatDate } from '../../utils/date';
import { applyFieldErrors, problemOf } from '../../utils/formErrors';
import { decimalRule } from '../../utils/money';
import { buildIncrementBody, groupRevisionsByYear } from './salaryRevisions';

interface IncrementValues { monthlySalary: string; effectiveFrom: Dayjs; remarks?: string }

function IncrementDialog({ employee, onClose }: { employee: Employee; onClose: () => void }) {
  const [form] = Form.useForm<IncrementValues>();
  const qc = useQueryClient();
  const [err, setErr] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (v: IncrementValues) =>
      employeeService.update(employee.id, buildIncrementBody(employee, { ...v, effectiveFrom: v.effectiveFrom.format('YYYY-MM-DD') })),
    onSuccess: () => {
      message.success('Salary revised');
      void qc.invalidateQueries({ queryKey: ['employee-salary-revisions'] });
      void qc.invalidateQueries({ queryKey: ['employees'] });
      onClose();
    },
    onError: (e) => { if (!applyFieldErrors(form, e, { salaryRemarks: 'remarks' })) setErr(problemOf(e)?.message ?? 'Could not save the salary'); },
  });
  return (
    <FormDialog open title="Add increment / revise salary" onClose={onClose} onSubmit={() => form.submit()} submitting={save.isPending}>
      <Form form={form} layout="vertical" initialValues={{ effectiveFrom: dayjs() }} onFinish={(v) => { setErr(null); save.mutate(v); }}>
        {err && <Alert type="error" showIcon message={err} style={{ marginBottom: 16 }} />}
        <Typography.Paragraph type="secondary">
          Current monthly salary: {formatCurrency(employee.monthlySalary)}. A revision on a date that already has one replaces it.
        </Typography.Paragraph>
        <Form.Item name="monthlySalary" label="New monthly salary" rules={[{ required: true, message: 'Required' }, decimalRule]}>
          <Input inputMode="decimal" prefix="₹" />
        </Form.Item>
        <Form.Item name="effectiveFrom" label="Effective from" rules={[{ required: true, message: 'Required' }]}>
          <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" />
        </Form.Item>
        <Form.Item name="remarks" label="Remarks"><Input.TextArea rows={2} maxLength={500} placeholder="Optional" /></Form.Item>
      </Form>
    </FormDialog>
  );
}

/** Salary revisions of one employee grouped by year, with the "Add increment" action. */
export function SalaryHistoryDialog({ employee, canRevise, onClose }: { employee: Employee | null; canRevise: boolean; onClose: () => void }) {
  const [adding, setAdding] = useState(false);
  const q = useQuery({
    queryKey: ['employee-salary-revisions', employee?.id], enabled: !!employee,
    queryFn: () => employeeService.salaryRevisions((employee as Employee).id),
  });
  const groups = useMemo(() => groupRevisionsByYear(q.data ?? []), [q.data]);
  return (
    <Drawer open={!!employee} title={`Salary history — ${employee?.employeeName ?? ''}`} onClose={onClose} width="min(560px, 100%)" destroyOnHidden>
      {employee && (
        <>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
            <span>Current: <strong>{formatCurrency(employee.monthlySalary)}</strong> / month</span>
            {canRevise && <Button type="primary" icon={<PlusOutlined />} onClick={() => setAdding(true)}>Add increment / revise salary</Button>}
          </div>
          {q.isError && <Alert type="error" showIcon message="Could not load salary history" />}
          {q.isLoading && <Spin />}
          {groups.map((g) => (
            <section key={g.year} aria-label={`Year ${g.year}`} style={{ marginBottom: 16 }}>
              <Typography.Title level={5} style={{ margin: '0 0 8px' }}>{g.year}</Typography.Title>
              {g.rows.map((r) => (
                <div key={r.id} data-testid="revision-row" style={{ padding: '8px 0', borderTop: '1px solid #f0f0f0' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <span>{formatDate(r.effectiveFrom)}</span>
                    <strong>{formatCurrency(r.monthlySalary)}</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Typography.Text type="secondary">{r.remarks ?? ''}</Typography.Text>
                    <Typography.Text type={r.change?.startsWith('-') ? 'danger' : 'success'}>
                      {r.change === null ? 'Initial' : `${r.change[0]}${formatCurrency(r.change.slice(1))}`}
                    </Typography.Text>
                  </div>
                </div>
              ))}
            </section>
          ))}
          {adding && <IncrementDialog employee={employee} onClose={() => setAdding(false)} />}
        </>
      )}
    </Drawer>
  );
}
