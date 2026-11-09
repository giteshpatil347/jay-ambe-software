import { Form, Input, Modal } from 'antd';

interface Props { open: boolean; expenseNumber?: string; loading?: boolean; onCancel: () => void; onConfirm: (reason: string) => void }

/** Cancel confirmation; the reason is mandatory. */
export function CancelExpenseModal({ open, expenseNumber, loading, onCancel, onConfirm }: Props) {
  const [form] = Form.useForm<{ reason: string }>();
  return (
    <Modal
      open={open} title={`Cancel expense ${expenseNumber ?? ''}`} okText="Cancel expense" okButtonProps={{ danger: true }} cancelText="Keep"
      confirmLoading={loading} onCancel={() => { form.resetFields(); onCancel(); }}
      onOk={() => form.validateFields().then((v) => onConfirm(v.reason.trim())).catch(() => undefined)} destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="reason" label="Reason"
          rules={[{ validator: (_, v) => (v && String(v).trim() ? Promise.resolve() : Promise.reject(new Error('Reason is required'))) }]}
        >
          <Input.TextArea rows={3} maxLength={500} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
