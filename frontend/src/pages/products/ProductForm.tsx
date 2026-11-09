import { useState } from 'react';
import { Button, Form, Input, Select, Space } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { ProductImage } from './ProductImage';
import { FormDialog } from '../../components/common/FormDialog';
import { productService } from '../../services/productService';
import { applyApiError } from '../../utils/apiError';
import type { Product, ProductInput } from '../../types/masterData';

const DECIMAL = /^\d{1,11}(\.\d{1,3})?$/;

interface Props { product: Product | null; open: boolean; onClose: () => void; canCreateCategory: boolean }

export function ProductForm({ product, open, onClose, canCreateCategory }: Props) {
  const [form] = Form.useForm<ProductInput>();
  const qc = useQueryClient();
  const { can } = usePermission();
  const [newCategory, setNewCategory] = useState('');
  const categories = useQuery({ queryKey: ['product-categories'], queryFn: productService.categories, enabled: open });

  const save = useMutation({
    mutationFn: (v: ProductInput) => (product ? productService.update(product.id, v) : productService.create(v)),
    onSuccess: () => { void qc.invalidateQueries({ queryKey: ['products'] }); onClose(); },
    onError: (e) => applyApiError(e, form),
  });
  const addCategory = useMutation({
    mutationFn: () => productService.createCategory(newCategory.trim()),
    onSuccess: (c) => {
      void qc.invalidateQueries({ queryKey: ['product-categories'] });
      form.setFieldValue('categoryId', c.id);
      setNewCategory('');
    },
    onError: (e) => applyApiError(e),
  });

  const submit = () => form.validateFields().then((v) => save.mutate({
    ...v, minimumStock: v.minimumStock || undefined, description: v.description || undefined,
    productCode: product ? undefined : v.productCode || undefined,
  })).catch(() => undefined);

  return (
    <FormDialog open={open} title={product ? 'Edit material' : 'New material'} onClose={onClose}
      onSubmit={submit} submitting={save.isPending}>
      {product && <ProductImage productId={product.id} name={product.name} canUpdate={can('PRODUCT.UPDATE')} />}
      <Form form={form} layout="vertical" requiredMark="optional" preserve={false}
        initialValues={product ? { ...product, minimumStock: product.minimumStock ?? undefined } : { unit: 'KG' }}>
        {!product && (
          <Form.Item name="productCode" label="Material code" extra="Leave empty to auto-generate">
            <Input maxLength={50} />
          </Form.Item>
        )}
        <Form.Item name="name" label="Name" rules={[{ required: true, message: 'Name is required' }]}>
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="categoryId" label="Category">
          <Select allowClear loading={categories.isLoading} placeholder="Select category" optionFilterProp="label"
            options={categories.data?.map((c) => ({ value: c.id, label: c.name }))}
            dropdownRender={canCreateCategory ? (menu) => (
              <>
                {menu}
                <Space.Compact style={{ width: '100%', padding: 8 }}>
                  <Input placeholder="New category" value={newCategory} maxLength={100}
                    onChange={(e) => setNewCategory(e.target.value)} onKeyDown={(e) => e.stopPropagation()} />
                  <Button type="primary" disabled={!newCategory.trim()} loading={addCategory.isPending}
                    onClick={() => addCategory.mutate()}>Add</Button>
                </Space.Compact>
              </>
            ) : undefined} />
        </Form.Item>
        <Form.Item name="unit" label="Unit" rules={[{ required: true }]}
          extra={product ? 'The unit cannot change once the material has stock movements.' : undefined}>
          <Select options={[{ value: 'TON', label: 'Ton' }, { value: 'KG', label: 'Kg' }, { value: 'PIECE', label: 'Piece' }]} />
        </Form.Item>
        <Form.Item name="minimumStock" label="Minimum stock (low-stock alert)"
          rules={[{ pattern: DECIMAL, message: 'Enter a number with up to 3 decimals' }]}>
          <Input inputMode="decimal" />
        </Form.Item>
        <Form.Item name="description" label="Description">
          <Input.TextArea rows={3} maxLength={2000} />
        </Form.Item>
      </Form>
    </FormDialog>
  );
}
