import { useEffect, useState } from 'react';
import { Alert, Button, Flex, Image, Spin, Typography, Upload, message } from 'antd';
import { CameraOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { productService } from '../../services/productService';
import { humanSize } from '../../components/upload/attachmentRules';
import { problemOf, statusOf } from '../../utils/formErrors';

const MAX = 5 * 1024 * 1024;
const OK = ['image/jpeg', 'image/png', 'image/webp'];

export function ProductImage({ productId, name, canUpdate }: { productId: number; name: string; canUpdate: boolean }) {
  // Bytes come from the authenticated API (no URL token); the object URL is revoked when replaced or on unmount.
  const [version, setVersion] = useState(0);
  const [img, setImg] = useState<{ src?: string; error?: unknown; done: boolean }>({ done: false });
  useEffect(() => {
    let cancelled = false;
    let created: string | undefined;
    productService.imageBlob(productId).then((blob) => {
      if (cancelled) return;
      created = URL.createObjectURL(blob);
      setImg({ src: created, done: true });
    }).catch((e: unknown) => { if (!cancelled) setImg({ error: e, done: true }); });
    return () => { cancelled = true; if (created) URL.revokeObjectURL(created); };
  }, [productId, version]);
  const upload = useMutation({
    mutationFn: (f: File) => productService.uploadImage(productId, f),
    onSuccess: () => { void message.success('Image updated'); setVersion((v) => v + 1); },
    onError: (e) => { void message.error(problemOf(e)?.message ?? 'Could not upload the image'); },
  });
  const beforeUpload = (f: File) => {
    if (!OK.includes(f.type)) { void message.error('Use a JPG, PNG or WEBP image.'); return Upload.LIST_IGNORE; }
    if (f.size > MAX) { void message.error(`Image is ${humanSize(f.size)}; the limit is 5 MB.`); return Upload.LIST_IGNORE; }
    upload.mutate(f);
    return false;
  };
  const none = statusOf(img.error) === 404;
  const failed = img.error !== undefined && !none;

  return (
    <Flex gap={12} align="center" wrap style={{ marginBottom: 16 }}>
      <div style={{ width: 96, height: 96, border: '1px solid #f0f0f0', borderRadius: 8, display: 'grid', placeItems: 'center', overflow: 'hidden' }}>
        {!img.done ? <Spin /> : img.src ? (
          <Image src={img.src} alt={`Image of ${name}`} width={96} height={96} style={{ objectFit: 'cover' }}
            fallback="data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw==" />
        ) : <Typography.Text type="secondary" style={{ textAlign: 'center', fontSize: 12 }}>{none ? 'No image' : 'Unavailable'}</Typography.Text>}
      </div>
      <div style={{ flex: '1 1 160px' }}>
        {failed && <Alert type="warning" showIcon message="Could not load the image" style={{ marginBottom: 8 }} />}
        {canUpdate && (
          <Upload accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp" showUploadList={false} beforeUpload={beforeUpload} disabled={upload.isPending}>
            <Button icon={<CameraOutlined />} loading={upload.isPending} style={{ minHeight: 44 }}>{img.src ? 'Replace image' : 'Upload image'}</Button>
          </Upload>
        )}
        <Typography.Paragraph type="secondary" style={{ margin: '4px 0 0', fontSize: 12 }}>JPG, PNG or WEBP, up to 5 MB.</Typography.Paragraph>
      </div>
    </Flex>
  );
}
