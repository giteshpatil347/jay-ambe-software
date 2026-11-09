import { Form, Input, DatePicker, Radio, Select } from 'antd';
import dayjs from 'dayjs';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormDialog } from '../../components/common/FormDialog';
import { paymentService } from '../../services/paymentService';
import { applyApiError } from '../../utils/apiError';
import { decimalRule } from '../../utils/money';
import { PartySelect } from './PartySelect';
import { buildPaymentBody, type PaymentFormValues } from './paymentForm';

interface Props { open: boolean; onClose: () => void; party?: { id: number; name: string } }

const MODES = ['CASH', 'UPI', 'BANK_TRANSFER', 'CHEQUE', 'OTHER'];

export function PaymentFormDialog({ open, onClose, party }: Props) {
  const [form] = Form.useForm<PaymentFormValues>();
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: (v: PaymentFormValues) => paymentService.create(buildPaymentBody(v)),
    onSuccess: () => {
      for (const k of ['payments', 'ledger', 'balances']) void qc.invalidateQueries({ queryKey: [k] });
      onClose();
    },
    onError: (e) => applyApiError(e, form),
  });
  const submit = () => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined);

  return (
    <FormDialog open={open} title="Record payment" onClose={onClose} onSubmit={submit} submitting={save.isPending} submitText="Record">
      <Form form={form} layout="vertical" requiredMark="optional" preserve={false}
        initialValues={{ direction: 'PAID', paymentDate: dayjs(), partyId: party?.id }}>
        <Form.Item name="partyId" label="Party" rules={[{ required: true, message: 'Party is required' }]}>
          <PartySelect onlyActive initial={party} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="direction" label="Direction" rules={[{ required: true }]}>
          <Radio.Group optionType="button" buttonStyle="solid" size="large"
            options={[{ value: 'PAID', label: 'We paid' }, { value: 'RECEIVED', label: 'We received' }]} />
        </Form.Item>
        <Form.Item name="paymentDate" label="Date" rules={[{ required: true, message: 'Date is required' }]}>
          <DatePicker style={{ width: '100%' }} format="DD MMM YYYY" allowClear={false} />
        </Form.Item>
        <Form.Item name="amount" label="Amount" rules={[{ required: true, message: 'Amount is required' }, decimalRule]}>
          <Input inputMode="decimal" prefix="₹" />
        </Form.Item>
        <Form.Item name="paymentMode" label="Payment mode">
          <Select allowClear options={MODES.map((m) => ({ value: m, label: m.replace('_', ' ') }))} />
        </Form.Item>
        <Form.Item name="referenceNumber" label="Reference number"><Input maxLength={100} /></Form.Item>
        <Form.Item name="remarks" label="Remarks"><Input.TextArea rows={2} maxLength={500} /></Form.Item>
      </Form>
    </FormDialog>
  );
}
