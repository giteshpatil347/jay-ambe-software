import { Alert, Button, Descriptions, Flex, Grid, Modal, Spin, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { ExportButtons } from '../common/ExportButtons';
import { reportService } from '../../services/reportService';
import { AttachmentPanel } from '../upload/AttachmentPanel';
import { materialService } from '../../services/materialService';
import type { TxType } from '../../types/material';
import { formatCurrency } from '../../utils/currency';
import { formatDate, formatDateTime } from '../../utils/date';
import { formatQuantity } from '../../utils/quantity';

interface Props {
  type: TxType; id: number | null; onClose: () => void;
  canEdit: boolean; canCancel: boolean; onEdit: (id: number) => void; onCancel: (id: number, number: string) => void;
}

/** Read-only centered detail dialog with items, cancellation info and the Edit/Cancel actions (POSTED only). */
export function TransactionDetail({ type, id, onClose, canEdit, canCancel, onEdit, onCancel }: Props) {
  const screens = Grid.useBreakpoint();
  const { can } = usePermission();
  const perm = type === 'IN' ? 'MATERIAL_IN' : 'MATERIAL_OUT';
  const q =useQuery({ queryKey: ['material-tx', type, id], queryFn: () => materialService.get(type, id as number), enabled: id !== null, gcTime: 0 });
  const tx = q.data;
  const party = type === 'IN' ? 'Supplier' : 'Client';
  return (
    <Modal open={id !== null} onCancel={onClose} centered destroyOnHidden width={screens.md ? 760 : 'calc(100vw - 16px)'}
      style={{ maxWidth: 'calc(100vw - 16px)' }} styles={{ body: { maxHeight: 'calc(100vh - 220px)', overflowY: 'auto', overflowX: 'hidden' } }}
      title={tx?.transactionNumber ?? 'Details'}
      footer={tx?.status === 'POSTED' ? (
        <Flex gap={8} justify="flex-end" wrap>
          {can('REPORT.EXPORT') && can(`${perm}.EXPORT`) && <ExportButtons formats={['pdf']} columnsKind="challan" onExport={(_fmt, columns) => reportService.challanPdf(tx.id, columns)} />}
          {canEdit && <Button onClick={() => onEdit(tx.id)}>Edit</Button>}
          {canCancel && <Button danger onClick={() => onCancel(tx.id, tx.transactionNumber)}>Cancel</Button>}
        </Flex>
      ) : null}>
      {q.isLoading && <Spin />}
      {q.isError && <Alert type="error" showIcon message="Could not load this entry" />}
      {tx && (
        <>
          {tx.status === 'CANCELLED' && (
            <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="Cancelled"
              description={`${tx.cancellationReason ?? ''}${tx.cancelledByName ? ` — ${tx.cancelledByName}` : ''}${tx.cancelledAt ? `, ${formatDateTime(tx.cancelledAt)}` : ''}`} />
          )}
          <Descriptions size="small" column={screens.md ? 2 : 1} bordered>
            <Descriptions.Item label="Entry Number">{tx.transactionNumber}</Descriptions.Item>
            <Descriptions.Item label="Date">{formatDate(tx.transactionDate)}</Descriptions.Item>
            <Descriptions.Item label={party}>{tx.partyName ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="Status"><Tag color={tx.status === 'POSTED' ? 'green' : 'default'}>{tx.status}</Tag></Descriptions.Item>
            <Descriptions.Item label="Challan no.">{tx.challanNumber || '—'}</Descriptions.Item>
            <Descriptions.Item label="Vehicle no.">{tx.vehicleNumber || '—'}</Descriptions.Item>
            <Descriptions.Item label="Driver">{tx.driverName || '—'}</Descriptions.Item>
            <Descriptions.Item label="Created by">{tx.createdByName ?? '—'}</Descriptions.Item>
          </Descriptions>
          <Typography.Title level={5} style={{ marginTop: 16 }}>Items</Typography.Title>
          <Table
            size="small" pagination={false} dataSource={tx.items} rowKey="id" scroll={{ x: 'max-content' }}
            columns={[
              { title: 'Material', render: (_: unknown, i) => `${i.productCode} · ${i.productName}` },
              { title: 'Qty', align: 'right', render: (_: unknown, i) => `${formatQuantity(i.quantity)} ${i.unit}` },
              { title: 'Rate', dataIndex: 'rate', align: 'right', render: (v: string) => formatCurrency(v) },
              { title: 'Amount', dataIndex: 'amount', align: 'right', render: (v: string) => formatCurrency(v) },
            ]}
            summary={() => (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3}><strong>Total</strong></Table.Summary.Cell>
                <Table.Summary.Cell index={1} align="right"><strong>{formatCurrency(tx.totalAmount)}</strong></Table.Summary.Cell>
              </Table.Summary.Row>
            )}
          />
          <Typography.Title level={5} style={{ marginTop: 16 }}>Challan / photos</Typography.Title>
          <AttachmentPanel
            ownerType="MATERIAL_TRANSACTION" ownerId={tx.id}
            canUpload={can(`${perm}.CREATE`) || can(`${perm}.UPDATE`)} canDelete={can(`${perm}.UPDATE`)}
            disabled={tx.status === 'CANCELLED'} disabledReason="This entry is cancelled; attachments are read-only."
          />
          <Typography.Title level={5} style={{ marginTop: 16 }}>Remarks</Typography.Title>
          <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{tx.remarks || '—'}</Typography.Paragraph>
        </>
      )}
    </Modal>
  );
}
