import { useState } from 'react';
import { Alert, Button, Collapse, Flex, Segmented, Select, Tag, Typography } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuth } from '../../auth/useAuth';
import { usePermission } from '../../auth/usePermission';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { roleService } from '../../services/roleService';
import { userService } from '../../services/userService';
import { applyApiError } from '../../utils/apiError';
import { moduleLabel, type ModuleGroup, type User } from '../../types/masterData';
import { buildOverridePayload, stateFromOverrides, type Choice, type MatrixState } from './matrix';

interface EditorProps { user: User; catalogue: ModuleGroup[]; roleGranted: Set<string>; readOnly: boolean }

export function MatrixEditor({ user, catalogue, roleGranted, readOnly }: EditorProps) {
  const qc = useQueryClient();
  const [state, setState] = useState<MatrixState>(() => stateFromOverrides(user.permissionOverrides));
  const save = useMutation({
    mutationFn: () => userService.setOverrides(user.id, buildOverridePayload(state)),
    onSuccess: (u) => {
      setState(stateFromOverrides(u.permissionOverrides));
      void qc.invalidateQueries({ queryKey: ['users'] });
    },
    onError: (e) => applyApiError(e),
  });
  const count = Object.values(state).filter((c) => c !== 'INHERIT').length;

  return (
    <Flex vertical gap={12}>
      <Collapse size="small" items={catalogue.map((g) => ({
        key: g.moduleCode, label: moduleLabel(g.moduleCode),
        children: (
          <Flex vertical gap={12}>
            {g.permissions.map((p) => {
              const choice: Choice = state[p.permissionCode] ?? 'INHERIT';
              return (
                <Flex key={p.permissionCode} justify="space-between" align="center" wrap gap={8}>
                  <Flex vertical>
                    <Typography.Text>{p.permissionCode}</Typography.Text>
                    <span>
                      {roleGranted.has(p.permissionCode) ? <Tag color="green">Granted by role</Tag> : <Tag>Not in roles</Tag>}
                    </span>
                  </Flex>
                  <Segmented<Choice> value={choice} disabled={readOnly}
                    options={[{ value: 'INHERIT', label: 'Inherit' }, { value: 'ALLOW', label: 'Allow' }, { value: 'DENY', label: 'Deny' }]}
                    onChange={(v) => setState((s) => ({ ...s, [p.permissionCode]: v }))} />
                </Flex>
              );
            })}
          </Flex>
        ),
      }))} />
      {!readOnly && (
        <Button type="primary" loading={save.isPending} onClick={() => save.mutate()}>Save overrides ({count})</Button>
      )}
    </Flex>
  );
}

export function PermissionMatrix() {
  const { can } = usePermission();
  const { user: me } = useAuth();
  const [search, setSearch] = useState('');
  const term = useDebouncedValue(search.trim());
  const [userId, setUserId] = useState<number>();

  const users = useQuery({ queryKey: ['users', 'picker', term], queryFn: () => userService.list({ search: term || undefined, page: 0, size: 50 }) });
  const user = useQuery({ queryKey: ['users', 'detail', userId], enabled: userId !== undefined, queryFn: () => userService.get(userId!) });
  const catalogue = useQuery({ queryKey: ['permission-catalogue'], queryFn: userService.permissionCatalogue, staleTime: 5 * 60_000 });
  const roles = useQuery({ queryKey: ['roles'], queryFn: roleService.list, enabled: can('ROLE.VIEW') });

  const isSelf = user.data !== undefined && String(user.data.id) === String(me?.id);
  const roleGranted = new Set(
    (roles.data ?? []).filter((r) => user.data?.roles.includes(r.roleCode)).flatMap((r) => r.permissionCodes),
  );

  return (
    <Flex vertical gap={16}>
      <Typography.Title level={3} style={{ margin: 0 }}>Permission overrides</Typography.Title>
      <Select showSearch filterOption={false} placeholder="Select a user" style={{ maxWidth: 420, width: '100%' }}
        onSearch={setSearch} value={userId} onChange={setUserId} loading={users.isLoading}
        options={users.data?.content.map((u) => ({ value: u.id, label: `${u.fullName} (${u.mobile})` }))} />
      {isSelf && <Alert type="info" showIcon message="You cannot change your own overrides." />}
      {user.data && catalogue.data && (
        <MatrixEditor key={`${user.data.id}-${user.dataUpdatedAt}`} user={user.data} catalogue={catalogue.data}
          roleGranted={roleGranted} readOnly={isSelf || !can('PERMISSION.MANAGE')} />
      )}
    </Flex>
  );
}
