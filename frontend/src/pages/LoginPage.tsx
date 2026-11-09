import { useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import { isAxiosError } from 'axios';
import { useAuth } from '../auth/useAuth';
import type { ProblemDetail } from '../api/types';
import { BrandMark } from '../components/common/BrandMark';

export function LoginPage() {
  const { user, login, flash, clearFlash } = useAuth();
  const navigate = useNavigate();
  const requested = (useLocation().state as { from?: string } | null)?.from;
  const from = requested && requested !== '/change-password' ? requested : '/';
  const [error, setError] = useState<string>();
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to={user.passwordChangeRequired ? '/change-password' : from} replace />;

  const onFinish = async (v: { mobile: string; password: string }) => {
    setBusy(true); setError(undefined); clearFlash();
    try {
      const u = await login(v.mobile, v.password);
      navigate(u.passwordChangeRequired ? '/change-password' : from, { replace: true });
    } catch (e) {
      const p = isAxiosError<ProblemDetail>(e) ? e.response?.data : undefined;
      setError(p?.message ?? 'Unable to sign in. Please try again.');
    } finally { setBusy(false); }
  };

  return (
    <div style={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', padding: 16 }}>
      <Card style={{ width: '100%', maxWidth: 380 }}>
        <Typography.Title level={3} style={{ display: 'flex', justifyContent: 'center' }}><BrandMark size={32} fontSize={24} /></Typography.Title>
        {flash && !error && <Alert type="success" message={flash} showIcon style={{ marginBottom: 16 }} />}
        {error && <Alert type="error" message={error} showIcon style={{ marginBottom: 16 }} />}
        <Form layout="vertical" onFinish={onFinish} requiredMark={false}>
          <Form.Item name="mobile" label="Mobile number" rules={[{ required: true, message: 'Enter your mobile number' }]}>
            <Input size="large" inputMode="tel" autoComplete="username" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true, message: 'Enter your password' }]}>
            <Input.Password size="large" autoComplete="current-password" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block loading={busy}>Sign in</Button>
        </Form>
      </Card>
    </div>
  );
}
