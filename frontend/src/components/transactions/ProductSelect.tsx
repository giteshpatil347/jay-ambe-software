import { useState } from 'react';
import { Select } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { productService } from '../../services/productService';
import type { ProductInfo } from './transactionLogic';

interface Props { value?: number; selected?: ProductInfo; onSelect: (p: ProductInfo) => void; onClear?: () => void; invalid?: boolean }

/** Server-side searchable product picker, ACTIVE products only. Shows the NAME only; typing a code still finds the product. */
export function ProductSelect({ value, selected, onSelect, onClear, invalid }: Props) {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const { data, isFetching } = useQuery({
    queryKey: ['products', 'select', q],
    queryFn: () => productService.list({ q: q || undefined, status: 'ACTIVE', page: 0, size: 30 }),
  });
  const found = (data?.content ?? []).map((p) => ({ id: p.id, code: p.code, name: p.name, unit: p.unit }));
  const options = found.map((p) => ({ value: p.id, label: p.name }));
  if (selected && !found.some((p) => p.id === selected.id)) options.unshift({ value: selected.id, label: selected.name });
  return (
    <Select
      showSearch allowClear={!!onClear} placeholder="Material" value={value} status={invalid ? 'error' : undefined} style={{ width: '100%' }}
      filterOption={false} onSearch={setSearch} onClear={onClear} loading={isFetching} options={options} aria-label="Material"
      onChange={(id) => { if (id === undefined) return; const p = found.find((x) => x.id === id) ?? (selected?.id === id ? selected : undefined); if (p) onSelect(p); }}
    />
  );
}
