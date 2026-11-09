import { Form, Input, Select, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { FormDialog } from '../../components/common/FormDialog';
import { roleService } from '../../services/roleService';
import { userService } from '../../services/userService';
import { applyApiError } from '../../utils/apiError';
import type { CreateUserInput, User } from '../../types/masterData';

const MOBILE_RULE = { pattern: /^\d{10,15}$/, message: 'Must be 10-15 digits' };
const PASSWORD_RULES = [{ required: true, message: 'Password is required' }, { min: 10, max: 72, message: '10-72 characters' }];

function useRoleOptions(enabled: boolean) {
  const roles = useQuery({ queryKey: ['roles'], queryFn: roleService.list, enabled });
  return { loading: roles.isLoading, options: roles.data?.map((r) => ({ value: r.roleCode, label: r.roleName })) };
}

export function UserFormDialog({ user, open, onClose }: { user: User | null; open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<CreateUserInput>();
  const qc = useQueryClient();
  const roles = useRoleOptions(open && !user);
  const save = useMutation({
    mutationFn: (v: CreateUserInput) => (user
      ? userService.update(user.id, { fullName: v.fullName, mobile: v.mobile, email: v.email || undefined })
      : userService.create({ ...v, email: v.email || undefined })),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['users'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  return (
    <FormDialog open={open} title={user ? 'Edit user' : 'New user'} onClose={onClose}
      onSubmit={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)} submitting={save.isPending}>
      <Form form={form} layout="vertical" requiredMark="optional" preserve={false}
        initialValues={user ? { fullName: user.fullName, mobile: user.mobile, email: user.email ?? undefined } : {}}>
        <Form.Item name="fullName" label="Full name" rules={[{ required: true, message: 'Name is required' }]}><Input maxLength={150} /></Form.Item>
        <Form.Item name="mobile" label="Mobile (login)" rules={[{ required: true, message: 'Mobile is required' }, MOBILE_RULE]}>
          <Input inputMode="tel" maxLength={15} />
        </Form.Item>
        <Form.Item name="email" label="Email" rules={[{ type: 'email', message: 'Invalid email' }]}><Input inputMode="email" maxLength={150} /></Form.Item>
        {!user && (
          <>
            <Form.Item name="password" label="Initial password" rules={PASSWORD_RULES}
              extra="The user must change it at first login."><Input.Password autoComplete="new-password" maxLength={72} /></Form.Item>
            <Form.Item name="roleCodes" label="Roles" rules={[{ required: true, message: 'Select at least one role' }]}>
              <Select mode="multiple" loading={roles.loading} options={roles.options} optionFilterProp="label" />
            </Form.Item>
          </>
        )}
      </Form>
    </FormDialog>
  );
}

export function AssignRolesDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const [form] = Form.useForm<{ roleCodes: string[] }>();
  const qc = useQueryClient();
  const roles = useRoleOptions(user !== null);
  const save = useMutation({
    mutationFn: (v: { roleCodes: string[] }) => userService.assignRoles(user!.id, v.roleCodes),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['users'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  return (
    <FormDialog open={user !== null} title={`Roles — ${user?.fullName ?? ''}`} onClose={onClose}
      onSubmit={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)} submitting={save.isPending}>
      <Form form={form} layout="vertical" preserve={false} initialValues={{ roleCodes: user?.roles ?? [] }}>
        <Form.Item name="roleCodes" label="Roles" rules={[{ required: true, message: 'Select at least one role' }]}>
          <Select mode="multiple" loading={roles.loading} options={roles.options} optionFilterProp="label" />
        </Form.Item>
      </Form>
    </FormDialog>
  );
}

export function ResetPasswordDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const [form] = Form.useForm<{ password: string }>();
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: (v: { password: string }) => userService.resetPassword(user!.id, v.password),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['users'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  return (
    <FormDialog open={user !== null} title={`Reset password — ${user?.fullName ?? ''}`} onClose={onClose}
      submitText="Reset" onSubmit={() => form.validateFields().then((v) => save.mutate(v)).catch(() => undefined)} submitting={save.isPending}>
      <Form form={form} layout="vertical" preserve={false}>
        <Form.Item name="password" label="New password" rules={PASSWORD_RULES}
          extra="The user is signed out everywhere and must change it at next login.">
          <Input.Password autoComplete="new-password" maxLength={72} />
        </Form.Item>
      </Form>
    </FormDialog>
  );
}

export function EffectivePermissionsDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const q = useQuery({
    queryKey: ['users', 'effective', user?.id], enabled: user !== null,
    queryFn: () => userService.effectivePermissions(user!.id),
  });
  return (
    <FormDialog open={user !== null} title={`Effective permissions — ${user?.fullName ?? ''}`}
      onClose={onClose} onSubmit={onClose} submitText="Close">
      {q.isLoading ? 'Loading…' : (
        <>
          <Typography.Paragraph type="secondary">Roles plus ALLOW overrides, minus DENY overrides.</Typography.Paragraph>
          {q.data?.map((p) => <Tag key={p} style={{ marginBottom: 4 }}>{p}</Tag>)}
        </>
      )}
    </FormDialog>
  );
}
