import { useState } from 'react';
import { Alert, Button, Card, DatePicker, Flex, Grid, Skeleton, Table, Typography } from 'antd';
import { ArrowLeftOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { Link, useParams } from 'react-router-dom';
import { usePermission } from '../../auth/usePermission';
import { ledgerService } from '../../services/ledgerService';
import type { LedgerEntry } from '../../types/payment';
import { formatDate, toIsoDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { ExportButtons } from '../../components/common/ExportButtons';
import { reportService } from '../../services/reportService';
import { PaymentFormDialog } from '../payments/PaymentFormDialog';
import { BalanceText, KindTag, amountCell } from './ledgerView';

export default function PartyLedgerPage() {
  const { id } = useParams();
  const partyId = Number(id);
  const { can } = usePermission();
  const screens = Grid.useBreakpoint();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [paying, setPaying] = useState(false);
  const from = range?.[0] ? toIsoDate(range[0]) : undefined;
  const to = range?.[1] ? toIsoDate(range[1]) : undefined;

  const q = useQuery({
    queryKey: ['ledger', partyId, from, to], queryFn: () => ledgerService.ledger(partyId, from, to),
    enabled: Number.isFinite(partyId), retry: false,
  });
  const d = q.data;

  const summary = (label: string, value: React.ReactNode) => (
    <Card size="small" style={{ flex: '1 1 160px' }}>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <div style={{ fontSize: 18, fontWeight: 600 }}>{value}</div>
    </Card>
  );

  return (
    <Flex vertical gap={12}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Flex align="center" gap={8}>
          <Link to="/suppliers" aria-label="Back to parties"><Button icon={<ArrowLeftOutlined />} style={{ minHeight: 44, minWidth: 44 }} /></Link>
          <Typography.Title level={4} style={{ margin: 0 }}>{d ? `${d.party.name} · Ledger` : 'Party ledger'}</Typography.Title>
        </Flex>
        {can('PAYMENT.CREATE') && d && (
          <Button type="primary" icon={<PlusOutlined />} style={{ minHeight: 44 }} onClick={() => setPaying(true)}>Record payment</Button>
        )}
      </Flex>

      <Flex gap={8} wrap align="center" justify="space-between">
        <DatePicker.RangePicker onChange={(v) => setRange(v)} style={{ maxWidth: 360, minHeight: 44 }} />
        {d && can('REPORT.EXPORT') && can('PARTY.VIEW') && (
          <ExportButtons columnsKind="ledger" onExport={(fmt, columns) => reportService.ledgerExport(partyId, fmt, from, to, columns)} />
        )}
      </Flex>

      {q.isError && <Alert type="error" showIcon message={problemOf(q.error)?.message ?? 'Could not load the ledger'} />}
      {q.isLoading && <Skeleton active />}

      {d && (
        <>
          <Flex gap={8} wrap>
            {summary('Opening balance', <BalanceText value={d.openingBalance} />)}
            {summary('Total debit', amountCell(d.totals.debit))}
            {summary('Total credit', amountCell(d.totals.credit))}
            {summary('Closing balance', <BalanceText value={d.closingBalance} strong />)}
          </Flex>

          {screens.md ? (
            <Table<LedgerEntry> size="middle" dataSource={d.entries} pagination={{ pageSize: 50, showSizeChanger: false, hideOnSinglePage: true }}
              rowKey={(e, i) => `${e.reference}-${i}`} scroll={{ x: 'max-content' }}
              columns={[
                { title: 'Date', dataIndex: 'date', render: formatDate },
                { title: 'Type', dataIndex: 'kind', render: (k) => <KindTag kind={k} /> },
                { title: 'Reference', dataIndex: 'reference' },
                { title: 'Debit', dataIndex: 'debit', align: 'right', render: amountCell },
                { title: 'Credit', dataIndex: 'credit', align: 'right', render: amountCell },
                { title: 'Balance', dataIndex: 'balance', align: 'right', render: (v: string) => <BalanceText value={v} /> },
              ]} />
          ) : (
            <Flex vertical gap={8}>
              {d.entries.length === 0 && <Typography.Text type="secondary">No entries in this range.</Typography.Text>}
              {d.entries.map((e, i) => (
                <Card size="small" key={`${e.reference}-${i}`}>
                  <Flex justify="space-between" gap={8}><KindTag kind={e.kind} /><Typography.Text type="secondary">{formatDate(e.date)}</Typography.Text></Flex>
                  <div style={{ margin: '4px 0' }}>{e.reference}</div>
                  <Flex justify="space-between" gap={8} wrap>
                    <span>Debit {amountCell(e.debit)}</span><span>Credit {amountCell(e.credit)}</span>
                  </Flex>
                  <div style={{ marginTop: 4 }}>Balance: <BalanceText value={e.balance} /></div>
                </Card>
              ))}
            </Flex>
          )}
        </>
      )}
      {paying && d && <PaymentFormDialog open party={{ id: d.party.id, name: d.party.name }} onClose={() => setPaying(false)} />}
    </Flex>
  );
}
