import { Typography } from 'antd';
import { useBranding } from '../../hooks/useBranding';

/** The business name, preceded by its icon when one is set. Used wherever the app name appears. */
export function BrandMark({ size = 24, fontSize = 16 }: { size?: number; fontSize?: number }) {
  const { name, iconUrl } = useBranding();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
      {iconUrl && <img src={iconUrl} alt="" width={size} height={size} style={{ objectFit: 'contain', borderRadius: 4, flex: 'none' }} />}
      <Typography.Text strong ellipsis style={{ fontSize }}>{name}</Typography.Text>
    </span>
  );
}
