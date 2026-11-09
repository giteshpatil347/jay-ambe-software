import { useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { isAxiosError } from 'axios';
import { useAuth } from '../../auth/useAuth';
import type { ProblemDetail } from '../../api/types';
import { BrandMark } from '../../components/common/BrandMark';

interface Values { currentPassword: string; newPassword: string; confirm: string }

export function ChangePasswordPage() {
  const { user, changePassword } = useAuth();
  const [form] = Form.useForm<Values>();
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  const onFinish = async (v: Values) => {
    setBusy(true); setError(undefined);
    try {
      await changePassword(v.currentPassword, v.newPassword); // ends the session -> /login with notice
    } catch (e) {
      const p = isAxiosError<ProblemDetail>(e) ? e.response?.data : undefined;
      if (p?.fieldErrors?.length) {
        form.setFields(p.fieldErrors.map((f) => ({ name: f.field as keyof Values, errors: [f.message] })));
      } else {
        setError(p?.message ?? 'Unable to change password. Please try again.');
      }
    } finally { setBusy(false); }
  };

  return (
    <div style={{ maxWidth: 420, margin: '0 auto', padding: 16 }}>
      <Card>
        <div style={{ marginBottom: 8 }}><BrandMark /></div>
        <Typography.Title level={4}>Change password</Typography.Title>
        {user?.passwordChangeRequired && (
          <Alert type="info" showIcon style={{ marginBottom: 16 }} message="You must set a new password before continuing." />
        )}
        {error && <Alert type="error" showIcon style={{ marginBottom: 16 }} message={error} />}
        <Form form={form} layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="currentPassword" label="Current password" rules={[{ required: true, message: 'Enter your current password' }]}>
            <Input.Password size="large" autoComplete="current-password" />
          </Form.Item>
          <Form.Item name="newPassword" label="New password" extra="At least 10 characters"
            rules={[{ required: true, message: 'Enter a new password' }, { min: 10, message: 'Must be at least 10 characters' }]}>
            <Input.Password size="large" autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="confirm" label="Confirm new password" dependencies={['newPassword']}
            rules={[{ required: true, message: 'Confirm the new password' },
              ({ getFieldValue }) => ({
                validator: (_, val) => (!val || getFieldValue('newPassword') === val ? Promise.resolve() : Promise.reject(new Error('Passwords do not match'))),
              })]}>
            <Input.Password size="large" autoComplete="new-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={busy}>Change password</Button>
        </Form>
      </Card>
    </div>
  );
}
