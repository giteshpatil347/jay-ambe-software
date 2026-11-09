import { Table, Typography } from 'antd';
import { diffValues, type DiffRow } from './diff';

const cell = (v: string | null) => (v === null ? <Typography.Text type="secondary">—</Typography.Text> : <code style={{ wordBreak: 'break-all' }}>{v}</code>);

/** Readable old -> new table of changed keys. */
export function AuditDiff({ oldValues, newValues }: { oldValues: unknown; newValues: unknown }) {
  const rows = diffValues(oldValues, newValues);
  if (rows.length === 0) return <Typography.Text type="secondary">No data changes recorded.</Typography.Text>;
  return (
    <Table<DiffRow> size="small" pagination={false} dataSource={rows} rowKey="key" scroll={{ x: 'max-content' }}
      columns={[
        { title: 'Field', dataIndex: 'key' },
        { title: 'Old', dataIndex: 'oldValue', render: cell },
        { title: 'New', dataIndex: 'newValue', render: cell },
      ]} />
  );
}
