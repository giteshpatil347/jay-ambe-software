import { useEffect, useState } from 'react';
import { Alert, Button, DatePicker, Drawer, Form, Input, Select, Space, Tag, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { FormDialog } from '../../components/common/FormDialog';
import { employeeService } from '../../services/employeeService';
import type { Employee, SalaryHistoryItem } from '../../types/expense';
import { formatCurrency } from '../../utils/currency';
import { formatDate } from '../../utils/date';
import { decimalRule } from '../../utils/money';
import { SalaryHistoryDialog } from './SalaryHistoryDialog';
import { applyFieldErrors, problemOf } from '../../utils/formErrors';

interface Values { employeeName: string; mobile?: string; monthlySalary?: string; joiningDate?: Dayjs | null; status?: string }

function SalaryPayments({ employee, onClose }: { employee: Employee | null; onClose: () => void }) {
  const [page, setPage] = useState(1);
  const q = useQuery({
    queryKey: ['employee-salary-history', employee?.id, page], enabled: !!employee,
    queryFn: () => employeeService.salaryHistory((employee as Employee).id, page, 10),
  });
  const mute = (r: SalaryHistoryItem) => (r.status === 'CANCELLED' ? { opacity: 0.5, textDecoration: 'line-through' } : undefined);
  return (
    <Drawer open={!!employee} title={`Salary payments — ${employee?.employeeName ?? ''}`} onClose={onClose} width="min(760px, 100%)" destroyOnHidden>
      <ResponsiveList<SalaryHistoryItem>
        data={q.data?.content ?? []} loading={q.isFetching} rowKey={(r) => r.expenseId}
        pagination={{ current: page, pageSize: 10, total: q.data?.totalElements ?? 0, showSizeChanger: false, onChange: setPage }}
        columns={[
          { title: 'Date', dataIndex: 'expenseDate', render: (v: string, r) => <span style={mute(r)}>{formatDate(v)}</span> },
          { title: 'Type', dataIndex: 'paymentType' },
          { title: 'Monthly salary', dataIndex: 'grossSalary', align: 'right', render: formatCurrency },
          { title: 'Advance adj.', dataIndex: 'advanceAdjustment', align: 'right', render: formatCurrency },
          { title: 'Paid', dataIndex: 'amount', align: 'right', render: (v: string, r) => <span style={mute(r)}>{formatCurrency(v)}</span> },
          { title: 'Status', dataIndex: 'status', render: (v: string) => <Tag color={v === 'POSTED' ? 'green' : 'default'}>{v}</Tag> },
        ]}
        renderCard={(r) => (
          <div style={r.status === 'CANCELLED' ? { opacity: 0.55 } : undefined}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><strong>{formatCurrency(r.amount)}</strong><Tag>{r.status}</Tag></div>
            <div>{r.paymentType} · {formatDate(r.expenseDate)}</div>
            <Typography.Text type="secondary">Monthly {formatCurrency(r.grossSalary)}{r.advanceAdjustment ? ` · Adj. ${formatCurrency(r.advanceAdjustment)}` : ''}</Typography.Text>
          </div>
        )}
      />
    </Drawer>
  );
}

export default function EmployeesPage() {
  const { can } = usePermission();
  const qc = useQueryClient();
  const [form] = Form.useForm<Values>();
  const [status, setStatus] = useState<string | undefined>();
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(1);
  const [editing, setEditing] = useState<Employee | null>(null);
  const [open, setOpen] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [payments, setPayments] = useState<Employee | null>(null);
  const [historyId, setHistoryId] = useState<number | null>(null);

  useEffect(() => { const t = setTimeout(() => { setQ(search); setPage(1); }, 400); return () => clearTimeout(t); }, [search]);
  const list = useQuery({
    queryKey: ['employees', 'list', status, q, page], placeholderData: (p) => p,
    queryFn: () => employeeService.list({ status, q: q || undefined, page, size: 20 }),
  });

  const openForm = (e: Employee | null) => {
    setEditing(e); setErr(null); setOpen(true); form.resetFields();
    if (e) form.setFieldsValue({ employeeName: e.employeeName, mobile: e.mobile ?? undefined, joiningDate: e.joiningDate ? dayjs(e.joiningDate) : null, status: e.status });
  };
  const save = useMutation({
    mutationFn: (v: Values) => {
      const body = { employeeName: v.employeeName.trim(), mobile: v.mobile?.trim() || undefined, joiningDate: v.joiningDate?.format('YYYY-MM-DD') };
      // editing never sends a salary: changes go through "Add increment" with an effective date
      return editing ? employeeService.update(editing.id, { ...body, status: v.status }) : employeeService.create({ ...body, monthlySalary: v.monthlySalary?.trim() });
    },
    onSuccess: () => { message.success('Employee saved'); setOpen(false); void qc.invalidateQueries({ queryKey: ['employees'] }); },
    onError: (e) => { if (!applyFieldErrors(form, e)) setErr(problemOf(e)?.message ?? 'Could not save the employee'); },
  });

  const tag = (e: Employee) => <Tag color={e.status === 'ACTIVE' ? 'green' : 'default'}>{e.status}</Tag>;
  const actions = (e: Employee) => (
    <Space size="small">
      <Button size="small" onClick={() => setHistoryId(e.id)}>Salary history</Button>
      <Button size="small" onClick={() => setPayments(e)}>Payments</Button>
      {can('EMPLOYEE.UPDATE') && <Button size="small" onClick={() => openForm(e)}>Edit</Button>}
    </Space>
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Employees</Typography.Title>
        {can('EMPLOYEE.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => openForm(null)}>New employee</Button>}
      </div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search placeholder="Search name / code / mobile" allowClear value={search} onChange={(e) => setSearch(e.target.value)} style={{ minWidth: 240 }} />
        <Select allowClear placeholder="Status" aria-label="Status filter" style={{ minWidth: 130 }} onChange={(v) => { setStatus(v); setPage(1); }}
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
      </Space>
      {list.isError && <Alert type="error" showIcon message="Could not load employees" style={{ marginBottom: 12 }} />}
      <ResponsiveList<Employee>
        data={list.data?.content ?? []} loading={list.isFetching} rowKey={(r) => r.id}
        pagination={{ current: page, pageSize: 20, total: list.data?.totalElements ?? 0, showSizeChanger: false, onChange: setPage }}
        columns={[
          { title: 'Code', dataIndex: 'employeeCode' },
          { title: 'Name', dataIndex: 'employeeName' },
          { title: 'Mobile', dataIndex: 'mobile' },
          { title: 'Monthly salary', dataIndex: 'monthlySalary', align: 'right', render: formatCurrency },
          { title: 'Joined', dataIndex: 'joiningDate', render: formatDate },
          { title: 'Status', dataIndex: 'status', render: (_: string, r) => tag(r) },
          { title: '', key: 'a', render: (_: unknown, r) => actions(r) },
        ]}
        renderCard={(r) => (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><strong>{r.employeeName}</strong>{tag(r)}</div>
            <Typography.Text type="secondary">{r.employeeCode}{r.mobile ? ` · ${r.mobile}` : ''}</Typography.Text>
            <div>Monthly salary: {formatCurrency(r.monthlySalary)}</div>
            <div style={{ marginTop: 6 }}>{actions(r)}</div>
          </div>
        )}
      />
      <FormDialog open={open} title={editing ? 'Edit employee' : 'New employee'} onClose={() => setOpen(false)} onSubmit={() => form.submit()} submitting={save.isPending}>
        <Form form={form} layout="vertical" onFinish={(v) => { setErr(null); save.mutate(v); }}>
          {err && <Alert type="error" showIcon message={err} style={{ marginBottom: 16 }} />}
          <Form.Item name="employeeName" label="Name" rules={[{ required: true, whitespace: true, message: 'Required' }]}><Input maxLength={150} /></Form.Item>
          <Form.Item name="mobile" label="Mobile"><Input inputMode="tel" maxLength={15} /></Form.Item>
          {!editing && (
            <Form.Item name="monthlySalary" label="Monthly salary" rules={[{ required: true, message: 'Required' }, decimalRule]}><Input inputMode="decimal" prefix="₹" /></Form.Item>
          )}
          <Form.Item name="joiningDate" label="Joining date"><DatePicker style={{ width: '100%' }} format="DD MMM YYYY" /></Form.Item>
          {editing && (
            <Form.Item name="status" label="Status">
              <Select aria-label="Status" options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
            </Form.Item>
          )}
        </Form>
      </FormDialog>
      <SalaryHistoryDialog key={historyId} employee={list.data?.content.find((e) => e.id === historyId) ?? null} canRevise={can('EMPLOYEE.UPDATE')} onClose={() => setHistoryId(null)} />
      <SalaryPayments key={payments?.id} employee={payments} onClose={() => setPayments(null)} />
    </div>
  );
}
