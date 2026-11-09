import { Form, Input, Modal } from 'antd';

interface Props { open: boolean; paymentNumber?: string; loading?: boolean; onCancel: () => void; onConfirm: (reason: string) => void }

/** Cancel confirmation; the reason is mandatory. */
export function CancelPaymentModal({ open, paymentNumber, loading, onCancel, onConfirm }: Props) {
  const [form] = Form.useForm<{ reason: string }>();
  return (
    <Modal open={open} title={`Cancel payment ${paymentNumber ?? ''}`} okText="Cancel payment" okButtonProps={{ danger: true }}
      cancelText="Keep" confirmLoading={loading} destroyOnHidden
      onCancel={() => { form.resetFields(); onCancel(); }}
      onOk={() => form.validateFields().then((v) => onConfirm(v.reason.trim())).catch(() => undefined)}>
      <Form form={form} layout="vertical">
        <Form.Item name="reason" label="Reason"
          rules={[{ validator: (_, v) => (v && String(v).trim() ? Promise.resolve() : Promise.reject(new Error('Reason is required'))) }]}>
          <Input.TextArea rows={3} maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
