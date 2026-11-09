import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Form, Spin, message } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { AttachmentPanel } from '../../components/upload/AttachmentPanel';
import { FormDialog } from '../../components/common/FormDialog';
import { expenseService } from '../../services/expenseService';
import { employeeService } from '../../services/employeeService';
import { applyFieldErrors, problemOf, statusOf } from '../../utils/formErrors';
import { ExpenseFormFields } from './ExpenseFormFields';
import { buildExpenseBody, expenseToFormValues, newExpenseDefaults, type ExpenseFormValues } from './expenseForm';

interface Props { open: boolean; expenseId?: number | null; onClose: () => void }

export function ExpenseFormModal({ open, expenseId, onClose }: Props) {
  const [form] = Form.useForm<ExpenseFormValues>();
  const { can } = usePermission();
  const qc = useQueryClient();
  const categoryId = Form.useWatch('categoryId', form);
  const [stale, setStale] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const editing = !!expenseId;

  const cats = useQuery({ queryKey: ['expense-categories', 'ACTIVE'], queryFn: () => expenseService.categories('ACTIVE'), enabled: open });
  const emps = useQuery({ queryKey: ['employees', 'ACTIVE'], queryFn: () => employeeService.options('ACTIVE'), enabled: open });
  const existing = useQuery({ queryKey: ['expense', expenseId], queryFn: () => expenseService.get(expenseId as number), enabled: open && editing, gcTime: 0 });

  const categories = useMemo(() => {
    const list = cats.data ?? [];
    // an inactive category of the record being edited must still be selectable/displayed
    if (existing.data && !list.some((c) => c.id === existing.data.categoryId)) {
      return [...list, { id: existing.data.categoryId, categoryCode: '', categoryName: existing.data.categoryName, categoryType: existing.data.categoryType, fixed: false, status: 'INACTIVE' as const }];
    }
    return list;
  }, [cats.data, existing.data]);
  const categoryType = categories.find((c) => c.id === categoryId)?.categoryType;

  useEffect(() => {
    if (!open) return;
    if (editing && existing.data) { form.setFieldsValue(expenseToFormValues(existing.data) as never); }
    if (!editing) { form.resetFields(); form.setFieldsValue(newExpenseDefaults() as never); }
  }, [open, editing, existing.data, form]);

  const save = useMutation({
    mutationFn: (v: ExpenseFormValues) => {
      const body = buildExpenseBody(v, categoryType, editing ? existing.data?.version : undefined);
      return editing ? expenseService.update(expenseId as number, body) : expenseService.create(body);
    },
    onSuccess: () => {
      message.success(editing ? 'Expense updated' : 'Expense recorded');
      void qc.invalidateQueries({ queryKey: ['expenses'] });
      void qc.invalidateQueries({ queryKey: ['employee-salary-history'] });
      onClose();
    },
    onError: (e) => {
      if (statusOf(e) === 409) { setStale(true); return; }
      if (applyFieldErrors(form, e)) return;
      setFormError(problemOf(e)?.message ?? 'Could not save the expense');
    },
  });

  const reload = () => { setStale(false); void existing.refetch(); };
  const loading = editing && existing.isLoading;

  return (
    <FormDialog open={open} title={editing ? 'Edit expense' : 'New expense'} onClose={onClose} onSubmit={() => form.submit()} submitting={save.isPending}>
      {loading ? <Spin /> : (
        <Form form={form} layout="vertical" onFinish={(v) => { setFormError(null); save.mutate(v); }} requiredMark="optional">
          {stale && (
            <Alert
              type="warning" showIcon style={{ marginBottom: 16 }}
              message="This expense was modified by someone else."
              description="Reload to see the latest version, then re-apply your changes."
              action={<Button size="small" onClick={reload}>Reload</Button>}
            />
          )}
          {formError && <Alert type="error" showIcon style={{ marginBottom: 16 }} message={formError} />}
          <ExpenseFormFields
            form={form} categories={categories} employees={emps.data ?? []} categoryType={categoryType}
            categoryLocked={editing} onCategoryChange={() => undefined}
          />
        </Form>
      )}
      {editing && existing.data && can('EXPENSE.VIEW') && (
        <div style={{ marginTop: 16 }}>
          <AttachmentPanel ownerType="EXPENSE" ownerId={existing.data.id}
            canUpload={can(['EXPENSE.CREATE', 'EXPENSE.UPDATE'])} canDelete={can('EXPENSE.UPDATE')}
            disabled={existing.data.status === 'CANCELLED'} />
        </div>
      )}
    </FormDialog>
  );
}
