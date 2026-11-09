import { useState, type CSSProperties } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import type { PartyType } from '../../types/masterData';
import { partyService } from '../../services/partyService';

interface Props {
  value?: number; onChange?: (v: number | undefined) => void; allowClear?: boolean; placeholder?: string;
  /** Option to show before the server list resolves (prefilled party). */
  initial?: { id: number; name: string };
  onlyActive?: boolean; type?: PartyType; style?: CSSProperties;
}

/** Server-side searchable party picker (debounced). */
export function PartySelect({ value, onChange, allowClear, placeholder = 'Select party', initial, onlyActive, type, style }: Props) {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const { data, isFetching } = useQuery({
    queryKey: ['parties', 'select', q, onlyActive, type],
    queryFn: () => partyService.list({ q: q || undefined, status: onlyActive ? 'ACTIVE' : undefined, type, page: 0, size: 30 }),
  });
  const options = (data?.content ?? []).map((p) => ({ value: p.id, label: `${p.name} (${p.code})` }));
  if (initial && !options.some((o) => o.value === initial.id)) options.unshift({ value: initial.id, label: initial.name });
  return (
    <Select showSearch allowClear={allowClear} placeholder={placeholder} value={value} onChange={onChange} style={style}
      filterOption={false} onSearch={setSearch} loading={isFetching} options={options} aria-label="Party" />
  );
}
