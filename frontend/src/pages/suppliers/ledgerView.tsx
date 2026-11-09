import { Tag, Typography } from 'antd';
import type { LedgerKind } from '../../types/payment';
import { balanceColor, formatBalance, isZeroMoney } from '../../utils/balance';
import { formatCurrency } from '../../utils/currency';

export const KIND_LABEL: Record<LedgerKind, string> = {
  PURCHASE: 'Purchase', DISPATCH: 'Dispatch', PAYMENT_PAID: 'Payment paid', PAYMENT_RECEIVED: 'Payment received',
};
export const KIND_COLOR: Record<LedgerKind, string> = {
  PURCHASE: 'geekblue', DISPATCH: 'purple', PAYMENT_PAID: 'orange', PAYMENT_RECEIVED: 'cyan',
};
export const KindTag = ({ kind }: { kind: LedgerKind }) => <Tag color={KIND_COLOR[kind]}>{KIND_LABEL[kind]}</Tag>;

/** Zero amounts render as an em dash so debit/credit columns stay scannable. */
export const amountCell = (v: string) => (isZeroMoney(v) ? '—' : formatCurrency(v));

/** Balance rendered as "Receivable ₹x" / "Payable ₹x", coloured. */
export const BalanceText = ({ value, strong }: { value: string; strong?: boolean }) => (
  <Typography.Text strong={strong} style={{ color: balanceColor(value) }}>{formatBalance(value)}</Typography.Text>
);
