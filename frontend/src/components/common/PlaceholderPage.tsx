import { Result } from 'antd';

export function PlaceholderPage({ title }: { title: string }) {
  return <Result status="info" title={title} subTitle="Coming soon" />;
}
