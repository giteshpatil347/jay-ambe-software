import { Checkbox, Collapse, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { userService } from '../../services/userService';
import { moduleLabel } from '../../types/masterData';

interface Props { value?: string[]; onChange?: (v: string[]) => void; disabled?: boolean }

/** Grouped-by-module permission checkboxes; usable as a Form.Item control. */
export function PermissionPicker({ value = [], onChange, disabled }: Props) {
  const catalogue = useQuery({ queryKey: ['permission-catalogue'], queryFn: userService.permissionCatalogue, staleTime: 5 * 60_000 });
  if (catalogue.isLoading) return <Typography.Text type="secondary">Loading permissions…</Typography.Text>;
  const toggleModule = (codes: string[], on: boolean) =>
    onChange?.(on ? Array.from(new Set([...value, ...codes])) : value.filter((c) => !codes.includes(c)));
  return (
    <Collapse size="small" items={catalogue.data?.map((g) => {
      const codes = g.permissions.map((p) => p.permissionCode);
      const n = codes.filter((c) => value.includes(c)).length;
      return {
        key: g.moduleCode,
        label: `${moduleLabel(g.moduleCode)} (${n}/${codes.length})`,
        children: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <Checkbox disabled={disabled} checked={n === codes.length} indeterminate={n > 0 && n < codes.length}
              onChange={(e) => toggleModule(codes, e.target.checked)}>All</Checkbox>
            <Checkbox.Group disabled={disabled} value={value.filter((c) => codes.includes(c))}
              onChange={(sel) => onChange?.([...value.filter((c) => !codes.includes(c)), ...(sel as string[])])}
              style={{ display: 'flex', flexDirection: 'column', gap: 8 }}
              options={g.permissions.map((p) => ({ value: p.permissionCode, label: p.actionCode }))} />
          </div>
        ),
      };
    })} />
  );
}
