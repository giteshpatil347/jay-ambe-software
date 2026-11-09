import { useState } from 'react';
import { Button, Flex, Input, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { useAuth } from '../../auth/useAuth';
import { usePermission } from '../../auth/usePermission';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { userService } from '../../services/userService';
import { applyApiError } from '../../utils/apiError';
import type { Status, User } from '../../types/masterData';
import { AssignRolesDialog, EffectivePermissionsDialog, ResetPasswordDialog, UserFormDialog } from './UserDialogs';

const PAGE_SIZE = 20;

export function UsersPage() {
  const { can } = usePermission();
  const { user: me } = useAuth();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const searchTerm = useDebouncedValue(search.trim());
  const [status, setStatus] = useState<Status>();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<User | 'new' | null>(null);
  const [rolesFor, setRolesFor] = useState<User | null>(null);
  const [resetFor, setResetFor] = useState<User | null>(null);
  const [permsFor, setPermsFor] = useState<User | null>(null);

  const list = useQuery({
    queryKey: ['users', { searchTerm, status, page }],
    queryFn: () => userService.list({ search: searchTerm || undefined, status, page, size: PAGE_SIZE }),
  });
  const toggle = useMutation({
    mutationFn: (u: User) => userService.setActive(u.id, u.status !== 'ACTIVE'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['users'] }),
    onError: (e) => applyApiError(e),
  });

  const isSelf = (u: User) => String(u.id) === String(me?.id);
  const actions = (u: User) => (
    <Space wrap onClick={(e) => e.stopPropagation()}>
      {can('USER.UPDATE') && <Button onClick={() => setEditing(u)}>Edit</Button>}
      {can('ROLE.ASSIGN') && <Button onClick={() => setRolesFor(u)}>Roles</Button>}
      {can('PERMISSION.VIEW') && <Button onClick={() => setPermsFor(u)}>Permissions</Button>}
      {can('USER.UPDATE') && <Button onClick={() => setResetFor(u)}>Reset password</Button>}
      {can('USER.DEACTIVATE') && (
        <Popconfirm title={u.status === 'ACTIVE' ? 'Deactivate this user? They are signed out immediately.' : 'Activate this user?'}
          onConfirm={() => toggle.mutate(u)} disabled={isSelf(u)}>
          <Button danger={u.status === 'ACTIVE'} disabled={isSelf(u) && u.status === 'ACTIVE'}
            title={isSelf(u) ? 'You cannot deactivate yourself' : undefined}>
            {u.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
          </Button>
        </Popconfirm>
      )}
    </Space>
  );
  const statusTag = (s: Status) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>;
  const roleTags = (u: User) => u.roles.map((r) => <Tag key={r}>{r}</Tag>);

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Typography.Title level={3} style={{ margin: 0 }}>Users</Typography.Title>
        {can('USER.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>New user</Button>}
      </Flex>
      <Flex gap={8} wrap>
        <Input.Search allowClear placeholder="Search name or mobile" value={search} style={{ flex: '1 1 220px', maxWidth: 360 }}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        <Select allowClear placeholder="Status" style={{ minWidth: 130, flex: '1 1 120px' }} value={status}
          onChange={(v) => { setStatus(v); setPage(0); }}
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
      </Flex>
      <ResponsiveList<User>
        data={list.data?.content ?? []} rowKey={(u) => u.id} loading={list.isLoading}
        pagination={{ current: (list.data?.page ?? page) + 1, pageSize: PAGE_SIZE, total: list.data?.totalElements ?? 0,
          showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'Name', dataIndex: 'fullName' },
          { title: 'Mobile', dataIndex: 'mobile' },
          { title: 'Roles', key: 'roles', render: (_: unknown, u) => roleTags(u) },
          { title: 'Status', dataIndex: 'status', render: statusTag },
          { title: '', key: 'actions', render: (_: unknown, u) => actions(u) },
        ]}
        renderCard={(u) => (
          <Flex vertical gap={6}>
            <Flex justify="space-between" gap={8}>
              <Typography.Text strong>{u.fullName}</Typography.Text>{statusTag(u.status)}
            </Flex>
            <Typography.Text type="secondary">{u.mobile}</Typography.Text>
            <div>{roleTags(u)}</div>
            {actions(u)}
          </Flex>
        )}
      />
      <UserFormDialog key={editing === 'new' ? 'new' : editing?.id ?? 'none'} open={editing !== null}
        user={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      <AssignRolesDialog key={`r${rolesFor?.id ?? ''}`} user={rolesFor} onClose={() => setRolesFor(null)} />
      <ResetPasswordDialog key={`p${resetFor?.id ?? ''}`} user={resetFor} onClose={() => setResetFor(null)} />
      <EffectivePermissionsDialog user={permsFor} onClose={() => setPermsFor(null)} />
    </Flex>
  );
}
