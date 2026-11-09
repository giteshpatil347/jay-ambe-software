import { useState } from 'react';
import { Button, Flex, Form, Input, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { FormDialog } from '../../components/common/FormDialog';
import { usePermission } from '../../auth/usePermission';
import { roleService } from '../../services/roleService';
import { applyApiError } from '../../utils/apiError';
import type { Role, RoleInput } from '../../types/masterData';
import { PermissionPicker } from './PermissionPicker';

/** The backend rejects any edit of SUPER_ADMIN (403). */
export const isLockedRole = (r: Role) => r.roleCode === 'SUPER_ADMIN';

function RoleForm({ role, open, onClose }: { role: Role | null; open: boolean; onClose: () => void }) {
  const [form] = Form.useForm<RoleInput>();
  const qc = useQueryClient();
  const save = useMutation({
    mutationFn: (v: RoleInput) => (role ? roleService.update(role.id, v) : roleService.create(v)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['roles'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  return (
    <FormDialog open={open} title={role ? `Edit role — ${role.roleCode}` : 'New role'} onClose={onClose}
      submitting={save.isPending}
      onSubmit={() => form.validateFields().then((v) => save.mutate({ ...v, description: v.description || undefined })).catch(() => undefined)}>
      <Form form={form} layout="vertical" requiredMark="optional" preserve={false}
        initialValues={role ? { ...role, description: role.description ?? undefined, permissionCodes: role.permissionCodes } : { permissionCodes: [] }}>
        {!role && (
          <Form.Item name="roleCode" label="Role code" extra="UPPER_SNAKE_CASE, cannot be changed later"
            rules={[{ required: true, message: 'Code is required' }, { pattern: /^[A-Z][A-Z0-9_]{1,49}$/, message: 'Must be UPPER_SNAKE_CASE' }]}>
            <Input maxLength={50} autoCapitalize="characters" />
          </Form.Item>
        )}
        <Form.Item name="roleName" label="Name" rules={[{ required: true, message: 'Name is required' }]}><Input maxLength={100} /></Form.Item>
        <Form.Item name="description" label="Description"><Input.TextArea rows={2} maxLength={1000} /></Form.Item>
        <Form.Item name="permissionCodes" label="Permissions"><PermissionPicker /></Form.Item>
      </Form>
    </FormDialog>
  );
}

export function RolesPage() {
  const { can } = usePermission();
  const [editing, setEditing] = useState<Role | 'new' | null>(null);
  const roles = useQuery({ queryKey: ['roles'], queryFn: roleService.list });

  const edit = (r: Role) => can('ROLE.UPDATE') && (
    <Button disabled={isLockedRole(r)} title={isLockedRole(r) ? 'SUPER_ADMIN cannot be edited' : undefined}
      onClick={(e) => { e.stopPropagation(); setEditing(r); }}>Edit</Button>
  );
  const tags = (r: Role) => (
    <>{r.system && <Tag color="blue">System</Tag>}<Tag>{r.permissionCodes.length} permissions</Tag></>
  );

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Typography.Title level={3} style={{ margin: 0 }}>Roles</Typography.Title>
        {can('ROLE.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>New role</Button>}
      </Flex>
      <ResponsiveList<Role>
        data={roles.data ?? []} rowKey={(r) => r.id} loading={roles.isLoading} pagination={false}
        columns={[
          { title: 'Code', dataIndex: 'roleCode' },
          { title: 'Name', dataIndex: 'roleName' },
          { title: 'Description', dataIndex: 'description', render: (v: string | null) => v ?? '—' },
          { title: 'Permissions', key: 'p', render: (_: unknown, r) => tags(r) },
          { title: '', key: 'a', render: (_: unknown, r) => edit(r) },
        ]}
        renderCard={(r) => (
          <Flex vertical gap={6}>
            <Typography.Text strong>{r.roleName}</Typography.Text>
            <Typography.Text type="secondary">{r.roleCode}</Typography.Text>
            <div>{tags(r)}</div>
            {edit(r)}
          </Flex>
        )}
      />
      <RoleForm key={editing === 'new' ? 'new' : editing?.id ?? 'none'} open={editing !== null}
        role={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
    </Flex>
  );
}
