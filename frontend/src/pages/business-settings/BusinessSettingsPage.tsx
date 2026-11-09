import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography, message } from 'antd';
import { PictureOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { BRANDING_KEY, useBranding } from '../../hooks/useBranding';
import { brandingService } from '../../services/brandingService';
import { applyApiError } from '../../utils/apiError';

const MAX_BYTES = 512 * 1024;
const TYPES = ['image/png', 'image/jpeg', 'image/webp'];
const TAP = { minHeight: 44 };

/** Icon rules mirrored from the server (which stays authoritative: magic bytes, size). Returns a friendly message or undefined. */
export function checkIcon(file: File): string | undefined {
  if (!TYPES.includes(file.type)) return 'Icon must be a PNG, JPG or WEBP image';
  if (file.size > MAX_BYTES) return 'Icon must be 512 KB or smaller';
  return undefined;
}

export default function BusinessSettingsPage() {
  const { name, iconUrl, loaded } = useBranding();
  const qc = useQueryClient();
  const [form] = Form.useForm<{ name: string }>();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File>();
  const [iconError, setIconError] = useState<string>();
  const seeded = useRef(false);

  useEffect(() => {
    if (loaded && !seeded.current) { seeded.current = true; form.setFieldsValue({ name }); }
  }, [name, loaded, form]);
  const preview = useMemo(() => (file ? URL.createObjectURL(file) : undefined), [file]);
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  const refresh = () => qc.invalidateQueries({ queryKey: BRANDING_KEY });
  const save = useMutation({
    mutationFn: (v: { name: string }) => brandingService.update(v.name.trim(), file),
    onSuccess: async () => { setFile(undefined); await refresh(); message.success('Business settings saved'); },
    onError: (e) => applyApiError(e, form),
  });
  const remove = useMutation({
    mutationFn: brandingService.removeIcon,
    onSuccess: async () => { await refresh(); message.success('Icon removed'); },
    onError: (e) => applyApiError(e),
  });

  const pick = (f?: File) => {
    if (!f) return;
    const problem = checkIcon(f);
    setIconError(problem);
    if (!problem) setFile(f);
    if (input.current) input.current.value = '';
  };
  const shown = preview ?? iconUrl;
  const canRemove = !!file || !!iconUrl;

  return (
    <div style={{ maxWidth: 520 }}>
      <Typography.Title level={4} style={{ marginTop: 0 }}>Business settings</Typography.Title>
      <Card>
        <Form form={form} layout="vertical" requiredMark={false} onFinish={(v) => save.mutate(v)}>
          <Form.Item name="name" label="Business name" extra="Shown in the top bar, on the sign-in page, in the browser tab and on exports"
            rules={[{ required: true, whitespace: true, message: 'Enter the business name' },
              { max: 60, message: 'At most 60 characters' }]}>
            <Input size="large" maxLength={60} autoComplete="organization" />
          </Form.Item>
          <Form.Item label="Icon" validateStatus={iconError ? 'error' : undefined} help={iconError}
            extra="PNG, JPG or WEBP, up to 512 KB. A square image works best.">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <div style={{ width: 64, height: 64, border: '1px solid #f0f0f0', borderRadius: 8, display: 'grid', placeItems: 'center', background: '#fafafa', overflow: 'hidden' }}>
                {shown
                  ? <img src={shown} alt="Current icon" width={64} height={64} style={{ objectFit: 'contain' }} />
                  : <PictureOutlined aria-label="No icon" style={{ fontSize: 24, color: '#bfbfbf' }} />}
              </div>
              <input ref={input} type="file" accept="image/png,image/jpeg,image/webp" style={{ display: 'none' }} aria-label="Icon file"
                onChange={(e) => pick(e.target.files?.[0])} />
              <Button icon={<UploadOutlined />} style={TAP} onClick={() => input.current?.click()}>Upload icon</Button>
              <Button danger style={TAP} disabled={!canRemove} loading={remove.isPending}
                onClick={() => (file ? setFile(undefined) : remove.mutate())}>Remove icon</Button>
            </div>
            {file && <Typography.Text type="secondary" style={{ display: 'block', marginTop: 8 }}>{file.name} will be uploaded when you save</Typography.Text>}
          </Form.Item>
          {save.isError && !save.error?.message && <Alert type="error" message="Could not save" />}
          <Button type="primary" htmlType="submit" size="large" block loading={save.isPending}>Save</Button>
        </Form>
      </Card>
    </div>
  );
}
