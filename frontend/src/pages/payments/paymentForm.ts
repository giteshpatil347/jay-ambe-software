import type { Dayjs } from 'dayjs';
import { toIsoDate } from '../../utils/date';
import type { PaymentBody, PaymentDirection } from '../../types/payment';

export interface PaymentFormValues {
  partyId: number; paymentDate: Dayjs; direction: PaymentDirection; amount: string;
  paymentMode?: string; referenceNumber?: string; remarks?: string;
}
const blank = (s?: string) => (s && s.trim() ? s.trim() : undefined);

/** Form values -> API body. Amount stays a string. */
export const buildPaymentBody = (v: PaymentFormValues): PaymentBody => ({
  partyId: v.partyId, paymentDate: toIsoDate(v.paymentDate), direction: v.direction, amount: v.amount.trim(),
  paymentMode: blank(v.paymentMode), referenceNumber: blank(v.referenceNumber), remarks: blank(v.remarks),
});
