import { useState } from 'react';
import { Alert, Button, Form, Input, Select, Tag, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { FormDialog } from '../../components/common/FormDialog';
import { expenseService } from '../../services/expenseService';
import type { ExpenseCategory } from '../../types/expense';
import { applyFieldErrors, problemOf } from '../../utils/formErrors';

interface Values { categoryCode?: string; categoryName: string; categoryType?: string; status?: string }

export default function ExpenseCategoriesPage() {
  const { can } = usePermission();
  const qc = useQueryClient();
  const [form] = Form.useForm<Values>();
  const [editing, setEditing] = useState<ExpenseCategory | null>(null);
  const [open, setOpen] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const list = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories() });
  const canManage = can('EXPENSE_CATEGORY.MANAGE');

  const openForm = (c: ExpenseCategory | null) => {
    setEditing(c); setErr(null); setOpen(true);
    form.resetFields();
    if (c) form.setFieldsValue({ categoryName: c.categoryName, status: c.status });
    else form.setFieldsValue({ categoryType: 'GENERAL' });
  };
  const save = useMutation({
    mutationFn: (v: Values) => editing
      ? expenseService.updateCategory(editing.id, { categoryName: v.categoryName.trim(), status: v.status as string })
      : expenseService.createCategory({ categoryCode: (v.categoryCode ?? '').trim(), categoryName: v.categoryName.trim(), categoryType: v.categoryType as string }),
    onSuccess: () => { message.success('Category saved'); setOpen(false); void qc.invalidateQueries({ queryKey: ['expense-categories'] }); },
    onError: (e) => { if (!applyFieldErrors(form, e)) setErr(problemOf(e)?.message ?? 'Could not save the category'); },
  });

  const tag = (c: ExpenseCategory) => <Tag color={c.status === 'ACTIVE' ? 'green' : 'default'}>{c.status}</Tag>;
  const edit = (c: ExpenseCategory) => canManage && <Button size="small" onClick={() => openForm(c)}>Edit</Button>;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Expense categories</Typography.Title>
        {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => openForm(null)}>New category</Button>}
      </div>
      {list.isError && <Alert type="error" showIcon message="Could not load categories" style={{ marginBottom: 12 }} />}
      <ResponsiveList<ExpenseCategory>
        data={list.data ?? []} loading={list.isFetching} rowKey={(r) => r.id}
        columns={[
          { title: 'Code', dataIndex: 'categoryCode' },
          { title: 'Name', dataIndex: 'categoryName', render: (v: string, r) => <>{v}{r.fixed && <Tag style={{ marginLeft: 8 }}>Fixed</Tag>}</> },
          { title: 'Type', dataIndex: 'categoryType' },
          { title: 'Status', dataIndex: 'status', render: (_: string, r) => tag(r) },
          { title: '', key: 'a', render: (_: unknown, r) => edit(r) },
        ]}
        renderCard={(r) => (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><strong>{r.categoryName}</strong>{tag(r)}</div>
            <Typography.Text type="secondary">{r.categoryCode} · {r.categoryType}{r.fixed ? ' · Fixed' : ''}</Typography.Text>
            <div style={{ marginTop: 6 }}>{edit(r)}</div>
          </div>
        )}
      />
      <FormDialog open={open} title={editing ? 'Edit category' : 'New category'} onClose={() => setOpen(false)} onSubmit={() => form.submit()} submitting={save.isPending}>
        <Form form={form} layout="vertical" onFinish={(v) => { setErr(null); save.mutate(v); }}>
          {err && <Alert type="error" showIcon message={err} style={{ marginBottom: 16 }} />}
          {!editing && (
            <>
              <Form.Item name="categoryCode" label="Code" extra="UPPER_SNAKE_CASE, cannot be changed later"
                rules={[{ required: true, message: 'Required' }, { pattern: /^[A-Z][A-Z0-9_]*$/, message: 'Use UPPER_SNAKE_CASE' }]}>
                <Input maxLength={50} />
              </Form.Item>
              <Form.Item name="categoryType" label="Type" rules={[{ required: true, message: 'Required' }]}>
                <Select aria-label="Type" options={['GENERAL', 'SALARY', 'ELECTRICITY'].map((v) => ({ value: v, label: v }))} />
              </Form.Item>
            </>
          )}
          <Form.Item name="categoryName" label="Name" rules={[{ required: true, whitespace: true, message: 'Required' }]}>
            <Input maxLength={100} disabled={!!editing?.fixed} />
          </Form.Item>
          {editing && (
            <Form.Item name="status" label="Status" extra={editing.fixed ? 'Fixed categories cannot be deactivated' : undefined}>
              <Select aria-label="Status" disabled={editing.fixed} options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
            </Form.Item>
          )}
        </Form>
      </FormDialog>
    </div>
  );
}
