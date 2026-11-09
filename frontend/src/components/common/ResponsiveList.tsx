import type { Key, ReactNode } from 'react';
import { Card, Grid, List, Table, type TableProps } from 'antd';

interface Props<T> {
  data: T[];
  columns: NonNullable<TableProps<T>['columns']>;
  /** Card body for mobile (< md). */
  renderCard: (row: T) => ReactNode;
  rowKey: (row: T) => Key;
  loading?: boolean;
  pagination?: TableProps<T>['pagination'];
  onRowClick?: (row: T) => void;
}

/** Table on >= md, card list on mobile. Shared by all list pages. */
export function ResponsiveList<T extends object>({ data, columns, renderCard, rowKey, loading, pagination, onRowClick }: Props<T>) {
  const screens = Grid.useBreakpoint();
  if (screens.md) {
    return (
      <Table<T>
        dataSource={data} columns={columns} rowKey={rowKey} loading={loading} pagination={pagination}
        scroll={{ x: 'max-content' }}
        onRow={onRowClick ? (r) => ({ onClick: () => onRowClick(r), style: { cursor: 'pointer' } }) : undefined}
      />
    );
  }
  return (
    <List<T>
      dataSource={data} loading={loading} pagination={pagination ? { ...pagination, position: 'bottom' } : undefined}
      renderItem={(row) => (
        <List.Item key={rowKey(row)} style={{ padding: '0 0 8px' }}>
          <Card size="small" style={{ width: '100%', minHeight: 44 }} onClick={onRowClick && (() => onRowClick(row))}>
            {renderCard(row)}
          </Card>
        </List.Item>
      )}
    />
  );
}
