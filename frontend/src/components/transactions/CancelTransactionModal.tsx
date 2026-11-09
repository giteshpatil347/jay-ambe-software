import { useState } from 'react';
import { Alert, Form, Input, Modal } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { materialService } from '../../services/materialService';
import type { TxType } from '../../types/material';
import { problemOf } from '../../utils/formErrors';

interface Props { type: TxType; id?: number; number?: string; open: boolean; onClose: () => void; onDone: () => void }

/** Cancel with a mandatory reason. A 409 (e.g. goods already dispatched) is shown in the dialog. */
export function CancelTransactionModal({ type, id, number, open, onClose, onDone }: Props) {
  const [form] = Form.useForm<{ reason: string }>();
  const qc = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const cancel = useMutation({
    mutationFn: (reason: string) => materialService.cancel(type, id as number, reason),
    onSuccess: () => {
      for (const k of ['material-in', 'material-out', 'inventory', 'products']) void qc.invalidateQueries({ queryKey: [k] });
      form.resetFields(); onDone();
    },
    onError: (e) => setError(problemOf(e)?.message ?? 'Could not cancel this entry'),
  });
  const close = () => { form.resetFields(); setError(null); onClose(); };
  return (
    <Modal
      open={open} title={`Cancel ${number ?? ''}`} okText="Cancel entry" okButtonProps={{ danger: true }} cancelText="Keep"
      confirmLoading={cancel.isPending} onCancel={close} destroyOnHidden
      onOk={() => form.validateFields().then((v) => { setError(null); cancel.mutate(v.reason.trim()); }).catch(() => undefined)}
    >
      <Form form={form} layout="vertical">
        {error && <Alert type="error" showIcon style={{ marginBottom: 12 }} message={error} />}
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
