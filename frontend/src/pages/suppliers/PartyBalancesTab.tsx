import { useState } from 'react';
import { Card, Flex, Select, Switch, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { usePermission } from '../../auth/usePermission';
import { dashboardService } from '../../services/dashboardService';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { ledgerService } from '../../services/ledgerService';
import type { PartyType } from '../../types/masterData';
import type { PartyBalance } from '../../types/payment';
import { balanceSide } from '../../utils/balance';
import { formatCurrency } from '../../utils/currency';
import { BalanceText } from './ledgerView';

const SIZE = 50;
const TYPE_LABEL: Record<PartyType, string> = { SUPPLIER: 'Supplier', CLIENT: 'Client', BOTH: 'Both' };

/** Sum of absolute balances for one side, from the loaded page (string math on cents). */
export function sumSide(rows: PartyBalance[], side: 'RECEIVABLE' | 'PAYABLE'): string {
  let cents = 0n;
  for (const r of rows) {
    if (balanceSide(r.balance) !== side) continue;
    const [i, f = ''] = r.balance.replace('-', '').split('.');
    cents += BigInt(i || '0') * 100n + BigInt((f + '00').slice(0, 2));
  }
  const s = cents.toString().padStart(3, '0');
  return `${s.slice(0, -2)}.${s.slice(-2)}`;
}

export function PartyBalancesTab() {
  const nav = useNavigate();
  const [onlyNonZero, setOnlyNonZero] = useState(true);
  const [type, setType] = useState<PartyType>();
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: ['balances', { onlyNonZero, type, page }],
    queryFn: () => ledgerService.balances({ onlyNonZero, type, page, size: SIZE }), placeholderData: (p) => p,
  });
  const { can } = usePermission();
  // All-party totals come from the dashboard when the user may see it; otherwise fall back to the loaded page.
  const dash = useQuery({ queryKey: ['dashboard'], queryFn: () => dashboardService.get(), enabled: can('DASHBOARD.VIEW'), retry: false });
  const allParty = !type && dash.data?.receivable !== undefined && dash.data?.payable !== undefined;
  const rows = q.data?.content ?? [];
  const multi = !allParty && (q.data?.totalElements ?? 0) > rows.length;
  const open = (r: PartyBalance) => nav(`/suppliers/${r.id}/ledger`);

  return (
    <Flex vertical gap={12}>
      <Flex gap={8} wrap>
        <Card size="small" style={{ flex: '1 1 180px' }}>
          <Typography.Text type="secondary">Receivable{multi ? ' (this page)' : ''}</Typography.Text>
          <div style={{ fontSize: 18, fontWeight: 600, color: '#389e0d' }}>{formatCurrency(allParty ? dash.data?.receivable : sumSide(rows, 'RECEIVABLE'))}</div>
        </Card>
        <Card size="small" style={{ flex: '1 1 180px' }}>
          <Typography.Text type="secondary">Payable{multi ? ' (this page)' : ''}</Typography.Text>
          <div style={{ fontSize: 18, fontWeight: 600, color: '#cf1322' }}>{formatCurrency(allParty ? dash.data?.payable : sumSide(rows, 'PAYABLE'))}</div>
        </Card>
      </Flex>
      <Flex gap={12} wrap align="center">
        <Select allowClear placeholder="Type" aria-label="Type filter" style={{ minWidth: 140 }} value={type}
          onChange={(v) => { setType(v); setPage(0); }}
          options={[{ value: 'SUPPLIER', label: 'Suppliers' }, { value: 'CLIENT', label: 'Clients' }]} />
        <Flex align="center" gap={8} style={{ minHeight: 44 }}>
          <Switch checked={onlyNonZero} onChange={(v) => { setOnlyNonZero(v); setPage(0); }} aria-label="Only non-zero balances" />
          <span>Only non-zero</span>
        </Flex>
      </Flex>
      <ResponsiveList<PartyBalance>
        data={rows} loading={q.isFetching} rowKey={(r) => r.id} onRowClick={open}
        pagination={{ current: page + 1, pageSize: SIZE, total: q.data?.totalElements ?? 0, showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Name', dataIndex: 'name' },
          { title: 'Type', dataIndex: 'type', render: (t: PartyType) => TYPE_LABEL[t] },
          { title: 'Balance', dataIndex: 'balance', align: 'right', render: (v: string) => <BalanceText value={v} /> },
        ]}
        renderCard={(r) => (
          <Flex justify="space-between" align="center" gap={8} style={{ minHeight: 44 }}>
            <Flex vertical><Typography.Text strong>{r.name}</Typography.Text>
              <Typography.Text type="secondary">{r.code} · {TYPE_LABEL[r.type]}</Typography.Text></Flex>
            <BalanceText value={r.balance} />
          </Flex>
        )}
      />
    </Flex>
  );
}
