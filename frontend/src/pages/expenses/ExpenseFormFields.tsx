import { Button, DatePicker, Form, Input, Select, type FormInstance } from 'antd';
import type { Dayjs } from 'dayjs';
import type { CategoryType, Employee, ExpenseCategory } from '../../types/expense';
import { employeeService } from '../../services/employeeService';
import { decimalRule, subtractMoney } from '../../utils/money';
import type { ExpenseFormValues } from './expenseForm';

const MODES = ['CASH', 'UPI', 'BANK', 'CHEQUE', 'OTHER'].map((v) => ({ value: v, label: v }));
/** Hours/days: positive, at most 2 decimals. */
const quantityRule = { pattern: /^(?!0+(\.0*)?$)\d{1,8}(\.\d{1,2})?$/, message: 'Enter a number above 0 (max 2 decimals)' };
const PAY_TYPES = ['MONTHLY', 'ADHOC', 'ADVANCE', 'SETTLEMENT'].map((v) => ({ value: v, label: v }));

interface Props {
  form: FormInstance<ExpenseFormValues>;
  categories: ExpenseCategory[];
  employees: Employee[];
  categoryType?: CategoryType;
  onCategoryChange: (id: number) => void;
  categoryLocked?: boolean;
}

/** Fields of the expense form; the salary / electricity block depends on the selected category type. */
export function ExpenseFormFields({ form, categories, employees, categoryType, onCategoryChange, categoryLocked }: Props) {
  const suggest = () => {
    const s = form.getFieldValue('salary') as ExpenseFormValues['salary'];
    const r = subtractMoney(s?.grossSalary, s?.advanceAdjustment);
    if (r !== null) form.setFieldValue('amount', r);
  };
  const usageType = Form.useWatch(['electricity', 'usageType'], form);
  /** Prefills the monthly salary in force for the employee on the expense date; failures just leave the field as is. */
  const prefillSalary = (employeeId?: number, date?: Dayjs) => {
    if (!employeeId || !date) return;
    employeeService.salaryAsOf(employeeId, date.format('YYYY-MM-DD'))
      .then((salary) => { if (salary !== null) form.setFieldValue(['salary', 'grossSalary'], salary); })
      .catch(() => undefined);
  };
  const onEmployee = (id: number) => prefillSalary(id, form.getFieldValue('expenseDate'));
  // changing the date re-prefills only a new expense whose salary the user has not typed
  const onDate = (d: Dayjs | null) => {
    if (!categoryLocked && d && !form.isFieldTouched(['salary', 'grossSalary'])) prefillSalary(form.getFieldValue(['salary', 'employeeId']), d);
  };
  return (
    <>
      <Form.Item name="expenseDate" label="Date" rules={[{ required: true, message: 'Required' }]}>
        <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" onChange={onDate} />
      </Form.Item>
      <Form.Item name="categoryId" label="Category" rules={[{ required: true, message: 'Required' }]}>
        <Select
          aria-label="Category" disabled={categoryLocked} onChange={onCategoryChange} showSearch optionFilterProp="label"
          options={categories.map((c) => ({ value: c.id, label: c.categoryName }))}
        />
      </Form.Item>

      {categoryType === 'SALARY' && (
        <fieldset data-testid="salary-block" style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 12, marginBottom: 16 }}>
          <legend style={{ padding: '0 8px', fontSize: 14 }}>Salary</legend>
          <Form.Item name={['salary', 'employeeId']} label="Employee" rules={[{ required: true, message: 'Required' }]}>
            <Select
              aria-label="Employee" showSearch optionFilterProp="label" onChange={onEmployee}
              options={employees.map((e) => ({ value: e.id, label: `${e.employeeName} (${e.employeeCode})`, disabled: e.status !== 'ACTIVE' }))}
            />
          </Form.Item>
          <Form.Item name={['salary', 'paymentType']} label="Payment type" initialValue="MONTHLY" rules={[{ required: true, message: 'Required' }]}>
            <Select aria-label="Payment type" options={PAY_TYPES} />
          </Form.Item>
          <Form.Item name={['salary', 'grossSalary']} label="Monthly salary" rules={[{ required: true, message: 'Required' }, decimalRule]}>
            <Input inputMode="decimal" prefix="₹" />
          </Form.Item>
          <Form.Item name={['salary', 'advanceAdjustment']} label="Advance adjustment" rules={[decimalRule]}>
            <Input inputMode="decimal" prefix="₹" />
          </Form.Item>
          <Button size="small" onClick={suggest}>Set amount = monthly salary − adjustment</Button>
        </fieldset>
      )}

      {categoryType === 'ELECTRICITY' && (
        <fieldset data-testid="electricity-block" style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 12, marginBottom: 16 }}>
          <legend style={{ padding: '0 8px', fontSize: 14 }}>Electricity</legend>
          <Form.Item name={['electricity', 'connectionNumber']} label="Connection" rules={[{ required: true, message: 'Required' }]}>
            <Select aria-label="Connection" options={[{ value: '1_NO', label: 'Connection 1' }, { value: '2_NO', label: 'Connection 2' }]} />
          </Form.Item>
          <Form.Item name={['electricity', 'usageType']} label="Usage type" initialValue="HOURLY" rules={[{ required: true, message: 'Required' }]}>
            <Select aria-label="Usage type" options={[{ value: 'HOURLY', label: 'Hourly' }, { value: 'DAILY', label: 'Daily' }]} />
          </Form.Item>
          <Form.Item name={['electricity', 'usageQuantity']} label={usageType === 'DAILY' ? 'No. of days' : 'No. of hours'} rules={[{ required: true, message: 'Required' }, quantityRule]}>
            <Input inputMode="decimal" />
          </Form.Item>
        </fieldset>
      )}

      {categoryType === 'ELECTRICITY' ? (
        <Form.Item name="amount" label="Price (optional)" rules={[decimalRule]}>
          <Input inputMode="decimal" prefix="₹" />
        </Form.Item>
      ) : (
        <Form.Item name="amount" label="Amount" rules={[{ required: true, message: 'Required' }, decimalRule]}>
          <Input inputMode="decimal" prefix="₹" />
        </Form.Item>
      )}
      {categoryType !== 'ELECTRICITY' && <Form.Item name="paidTo" label="Paid to"><Input maxLength={150} /></Form.Item>}
      {categoryType !== 'SALARY' && categoryType !== 'ELECTRICITY' && (
        <Form.Item name="paymentMode" label="Payment mode"><Select aria-label="Payment mode" options={MODES} /></Form.Item>
      )}
      <Form.Item name="remarks" label="Remarks"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
    </>
  );
}
