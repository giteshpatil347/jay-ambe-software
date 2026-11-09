import dayjs from 'dayjs';

export const formatDate = (v?: string | null) => (v ? dayjs(v).format('DD MMM YYYY') : '');
export const formatDateTime = (v?: string | null) => (v ? dayjs(v).format('DD MMM YYYY, HH:mm') : '');
/** ISO date (yyyy-MM-dd) for API params. */
export const toIsoDate = (v: Date | dayjs.Dayjs) => dayjs(v).format('YYYY-MM-DD');
