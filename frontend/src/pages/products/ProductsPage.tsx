import { useState } from 'react';
import { Button, Flex, Input, Popconfirm, Select, Space, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { usePermission } from '../../auth/usePermission';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { productService } from '../../services/productService';
import { applyApiError } from '../../utils/apiError';
import { compareDecimal, formatQuantity } from '../../utils/quantity';
import type { Product, Status } from '../../types/masterData';
import { ProductForm } from './ProductForm';

const PAGE_SIZE = 20;
export const isLowStock = (p: Product) =>
  p.minimumStock !== null && p.availableStock !== null && compareDecimal(p.availableStock, p.minimumStock) < 0;

export function ProductsPage() {
  const { can } = usePermission();
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const [categoryId, setCategoryId] = useState<number>();
  const [status, setStatus] = useState<Status>();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Product | 'new' | null>(null);

  const categories = useQuery({ queryKey: ['product-categories'], queryFn: productService.categories });
  const list = useQuery({
    queryKey: ['products', { q, categoryId, status, page }],
    queryFn: () => productService.list({ q: q || undefined, categoryId, status, page, size: PAGE_SIZE }),
  });
  const toggle = useMutation({
    mutationFn: (p: Product) => productService.setActive(p.id, p.status !== 'ACTIVE'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
    onError: (e) => applyApiError(e),
  });

  const stock = (p: Product) => (
    <Space size={4} wrap>
      <span>{formatQuantity(p.availableStock) || '0'} {p.unit}</span>
      {isLowStock(p) && <Tag color="red">Low stock</Tag>}
    </Space>
  );
  const actions = (p: Product) => (
    <Space wrap onClick={(e) => e.stopPropagation()}>
      {can('PRODUCT.UPDATE') && <Button onClick={() => setEditing(p)}>Edit</Button>}
      {can('PRODUCT.DELETE') && (
        <Popconfirm title={p.status === 'ACTIVE' ? 'Deactivate this material?' : 'Activate this material?'} onConfirm={() => toggle.mutate(p)}>
          <Button danger={p.status === 'ACTIVE'}>{p.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>
        </Popconfirm>
      )}
    </Space>
  );
  const statusTag = (s: Status) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>;

  return (
    <Flex vertical gap={16}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Typography.Title level={3} style={{ margin: 0 }}>Materials</Typography.Title>
        {can('PRODUCT.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>New material</Button>}
      </Flex>
      <Flex gap={8} wrap>
        <Input.Search allowClear placeholder="Search name or code" value={search} style={{ flex: '1 1 220px', maxWidth: 360 }}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        <Select allowClear placeholder="Category" style={{ minWidth: 160, flex: '1 1 140px' }} value={categoryId}
          onChange={(v) => { setCategoryId(v); setPage(0); }}
          options={categories.data?.map((c) => ({ value: c.id, label: c.name }))} />
        <Select allowClear placeholder="Status" style={{ minWidth: 130, flex: '1 1 120px' }} value={status}
          onChange={(v) => { setStatus(v); setPage(0); }}
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
      </Flex>
      <ResponsiveList<Product>
        data={list.data?.content ?? []} rowKey={(p) => p.id} loading={list.isLoading}
        pagination={{ current: (list.data?.page ?? page) + 1, pageSize: PAGE_SIZE, total: list.data?.totalElements ?? 0,
          showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Name', dataIndex: 'name' },
          { title: 'Category', dataIndex: 'categoryName', render: (v: string | null) => v ?? '—' },
          { title: 'Available', key: 'stock', render: (_: unknown, p) => stock(p) },
          { title: 'Min stock', key: 'min', render: (_: unknown, p) => (p.minimumStock ? `${formatQuantity(p.minimumStock)} ${p.unit}` : '—') },
          { title: 'Status', dataIndex: 'status', render: statusTag },
          { title: '', key: 'actions', render: (_: unknown, p) => actions(p) },
        ]}
        renderCard={(p) => (
          <Flex vertical gap={6}>
            <Flex justify="space-between" gap={8}>
              <Typography.Text strong>{p.name}</Typography.Text>{statusTag(p.status)}
            </Flex>
            <Typography.Text type="secondary">{p.code}{p.categoryName ? ` · ${p.categoryName}` : ''}</Typography.Text>
            {stock(p)}
            {actions(p)}
          </Flex>
        )}
      />
      <ProductForm key={editing === 'new' ? 'new' : editing?.id ?? 'none'} open={editing !== null}
        product={editing === 'new' ? null : editing} onClose={() => setEditing(null)}
        canCreateCategory={can('PRODUCT.CREATE')} />
    </Flex>
  );
}
