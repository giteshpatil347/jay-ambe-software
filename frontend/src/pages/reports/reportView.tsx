import { Card, Flex, Typography } from 'antd';
import type { ReactNode } from 'react';
import type { QtyByUnit } from '../../types/report';
import { formatQuantity } from '../../utils/quantity';

export const StatCard = ({ label, children, testId }: { label: string; children: ReactNode; testId?: string }) => (
  <Card size="small" style={{ flex: '1 1 150px', minWidth: 0 }} data-testid={testId}>
    <Typography.Text type="secondary">{label}</Typography.Text>
    <div style={{ fontSize: 18, fontWeight: 600, overflowWrap: 'anywhere' }}>{children}</div>
  </Card>
);

/** Quantities are listed per unit and never summed across units. */
export function UnitQuantities({ items, empty = '—' }: { items: QtyByUnit[] | undefined; empty?: string }) {
  if (!items?.length) return <>{empty}</>;
  return (
    <Flex vertical gap={0} data-testid="qty-by-unit">
      {items.map((q) => <span key={q.unit}>{formatQuantity(q.quantity)} {q.unit}</span>)}
    </Flex>
  );
}
