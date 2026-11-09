import { Form, Input, Select } from 'antd';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormDialog } from '../../components/common/FormDialog';
import { partyService } from '../../services/partyService';
import { applyApiError } from '../../utils/apiError';
import type { Party, PartyInput } from '../../types/masterData';

interface Props { party: Party | null; open: boolean; onClose: () => void }

const blankToUndef = (s?: string) => (s && s.trim() ? s.trim() : undefined);

export function PartyForm({ party, open, onClose }: Props) {
  const [form] = Form.useForm<PartyInput>();
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: (v: PartyInput) => (party ? partyService.update(party.id, v) : partyService.create(v)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['parties'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  const submit = () => form.validateFields().then((v) => save.mutate({
    ...v, mobile: blankToUndef(v.mobile), email: blankToUndef(v.email), address: blankToUndef(v.address),
    gstNumber: blankToUndef(v.gstNumber)?.toUpperCase(), openingBalance: blankToUndef(v.openingBalance),
  })).catch(() => undefined);

  return (
    <FormDialog open={open} title={party ? 'Edit party' : 'New party'} onClose={onClose}
      onSubmit={submit} submitting={save.isPending}>
      <Form form={form} layout="vertical" requiredMark="optional" preserve={false}
        initialValues={party ? { ...party, openingBalance: party.openingBalance ?? undefined } : { type: 'SUPPLIER' }}>
        <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="type" label="Type" rules={[{ required: true }]}>
          <Select options={[{ value: 'SUPPLIER', label: 'Supplier' }, { value: 'CLIENT', label: 'Client' }, { value: 'BOTH', label: 'Both' }]} />
        </Form.Item>
        <Form.Item name="mobile" label="Mobile" rules={[{ pattern: /^(\+91)?[6-9]\d{9}$/, message: '10-digit Indian mobile' }]}>
          <Input inputMode="tel" maxLength={13} />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Invalid email' }]}>
          <Input inputMode="email" maxLength={150} />
        </Form.Item>
        <Form.Item name="gstNumber" label="GSTIN"><Input maxLength={15} style={{ textTransform: 'uppercase' }} /></Form.Item>
        <Form.Item name="address" label="Address"><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
        <Form.Item name="openingBalance" label="Opening balance (₹)"
          extra="Positive = party owes us. Locked once the party has transactions."
          rules={[{ pattern: /^-?\d{1,12}(\.\d{1,2})?$/, message: 'Enter an amount with up to 2 decimals' }]}>
          <Input inputMode="text" />
        </Form.Item>
      </Form>
    </FormDialog>
  );
}
