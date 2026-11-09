import { useQuery } from '@tanstack/react-query';
import { brandingService, DEFAULT_BRAND_NAME, ICON_URL } from '../services/brandingService';

export const BRANDING_KEY = ['branding'] as const;

export interface BrandingView { name: string; hasIcon: boolean; iconUrl?: string; loaded: boolean }

/** Public business name + icon; falls back to 'MaterialBook' while loading or when the request fails. */
export function useBranding(): BrandingView {
  const { data } = useQuery({ queryKey: BRANDING_KEY, queryFn: brandingService.get, staleTime: 5 * 60_000 });
  const name = data?.name?.trim() || DEFAULT_BRAND_NAME;
  const hasIcon = !!data?.hasIcon;
  return { name, hasIcon, loaded: !!data, iconUrl: hasIcon ? `${ICON_URL}?v=${data?.iconVersion ?? 0}` : undefined };
}
