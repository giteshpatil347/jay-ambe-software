import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { api } from '../../api/client';
import { BrandingHead } from '../../components/common/BrandingHead';
import { MainLayout } from '../../layout/MainLayout';
import { LoginPage } from '../LoginPage';
import BusinessSettingsPage from './BusinessSettingsPage';
import { useBranding } from '../../hooks/useBranding';
import { MENU } from '../../app/menu';

let permissions: string[] = ['SETTINGS.MANAGE', 'DASHBOARD.VIEW'];
vi.mock('../../auth/useAuth', () => ({ useAuth: () => ({ user: undefined, logout: vi.fn(), flash: undefined, clearFlash: vi.fn(), login: vi.fn() }) }));
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p?: string) => !p || permissions.includes(p) }) }));

const wrap = (ui: React.ReactNode, path = '/') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return { qc, ...render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter></QueryClientProvider>) };
};
const mockBranding = (data: unknown) => vi.spyOn(api, 'get').mockImplementation(() => (data ? Promise.resolve({ data }) : Promise.reject(new Error('down'))) as never);

afterEach(() => { vi.restoreAllMocks(); permissions = ['SETTINGS.MANAGE', 'DASHBOARD.VIEW']; document.title = ''; });

function Probe() { const b = useBranding(); return <p>{b.name}|{String(b.iconUrl)}</p>; }

describe('useBranding', () => {
  it('falls back to MaterialBook while loading and when the request fails', async () => {
    mockBranding(undefined);
    wrap(<Probe />);
    expect(screen.getByText('MaterialBook|undefined')).toBeInTheDocument();
    await waitFor(() => expect(api.get).toHaveBeenCalled());
    expect(screen.getByText('MaterialBook|undefined')).toBeInTheDocument();
  });
  it('returns name and versioned icon url', async () => {
    mockBranding({ name: 'Shree Steel', hasIcon: true, iconVersion: 42 });
    wrap(<Probe />);
    expect(await screen.findByText('Shree Steel|/api/v1/settings/branding/icon?v=42')).toBeInTheDocument();
  });
});

describe('brand in the shell', () => {
  const shell = () => <Routes><Route element={<MainLayout />}><Route path="*" element={<p>page</p>} /></Route></Routes>;
  it('top bar shows name and icon', async () => {
    mockBranding({ name: 'Shree Steel', hasIcon: true, iconVersion: 7 });
    const { container } = wrap(shell());
    expect(await screen.findByText('Shree Steel')).toBeInTheDocument();
    await waitFor(() => expect(container.querySelector('img')).toHaveAttribute('src', '/api/v1/settings/branding/icon?v=7'));
  });
  it('top bar shows just the name without an icon', async () => {
    mockBranding({ name: 'Plain Co', hasIcon: false, iconVersion: 0 });
    const { container } = wrap(shell());
    expect(await screen.findByText('Plain Co')).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
  it('login page heading uses the branding, default before it loads', async () => {
    mockBranding({ name: 'Login Co', hasIcon: true, iconVersion: 1 });
    const { container } = wrap(<LoginPage />);
    expect(screen.getByText('MaterialBook')).toBeInTheDocument();
    expect(await screen.findByText('Login Co')).toBeInTheDocument();
    expect(container.querySelector('img')).toHaveAttribute('src', expect.stringContaining('?v=1'));
  });
});

describe('document title and favicon', () => {
  it('updates both and restores the default favicon when the icon is gone', async () => {
    const link = document.createElement('link');
    link.rel = 'icon'; link.href = '/icon.svg'; link.type = 'image/svg+xml';
    document.head.appendChild(link);
    const spy = mockBranding({ name: 'Tab Co', hasIcon: true, iconVersion: 3 });
    const { qc } = wrap(<BrandingHead />);
    await waitFor(() => expect(document.title).toBe('Tab Co'));
    expect(link.getAttribute('href')).toBe('/api/v1/settings/branding/icon?v=3');
    spy.mockImplementation((() => Promise.resolve({ data: { name: 'Tab Co', hasIcon: false, iconVersion: 0 } })) as never);
    await qc.invalidateQueries({ queryKey: ['branding'] });
    await waitFor(() => expect(link.getAttribute('href')).toBe('/icon.svg'));
    expect(link.getAttribute('type')).toBe('image/svg+xml');
    link.remove();
  });
});

describe('menu', () => {
  it('lists Business settings in the admin group with SETTINGS.MANAGE', () => {
    expect(MENU.find((m) => m.path === '/business-settings')).toMatchObject({ label: 'Business settings', permission: 'SETTINGS.MANAGE', group: 'admin' });
  });
});

describe('BusinessSettingsPage', () => {
  const png = () => new File([new Uint8Array([137, 80, 78, 71])], 'logo.png', { type: 'image/png' });

  it('requires a name and sends nothing when blank', async () => {
    mockBranding({ name: 'Old Co', hasIcon: false, iconVersion: 0 });
    const put = vi.spyOn(api, 'put');
    const user = userEvent.setup();
    wrap(<BusinessSettingsPage />);
    const name = await screen.findByLabelText('Business name');
    await waitFor(() => expect(name).toHaveValue('Old Co'));
    expect(name).toHaveAttribute('maxlength', '60');
    await user.clear(name);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Enter the business name')).toBeInTheDocument();
    expect(put).not.toHaveBeenCalled();
  });

  it('rejects wrong type and oversize icons client-side with friendly messages', async () => {
    mockBranding({ name: 'Old Co', hasIcon: false, iconVersion: 0 });
    const user = userEvent.setup({ applyAccept: false });
    wrap(<BusinessSettingsPage />);
    const input = await screen.findByLabelText('Icon file');
    await user.upload(input, new File(['<svg/>'], 'a.svg', { type: 'image/svg+xml' }));
    expect(await screen.findByText('Icon must be a PNG, JPG or WEBP image')).toBeInTheDocument();
    await user.upload(input, new File([new Uint8Array(512 * 1024 + 1)], 'big.png', { type: 'image/png' }));
    expect(await screen.findByText('Icon must be 512 KB or smaller')).toBeInTheDocument();
  });

  it('saves name + icon as multipart and refreshes the branding query', async () => {
    mockBranding({ name: 'Old Co', hasIcon: false, iconVersion: 0 });
    const put = vi.spyOn(api, 'put').mockResolvedValue({ data: { name: 'New Co', hasIcon: true, iconVersion: 9 } } as never);
    const user = userEvent.setup();
    wrap(<BusinessSettingsPage />);
    const name = await screen.findByLabelText('Business name');
    await waitFor(() => expect(name).toHaveValue('Old Co'));
    await user.clear(name);
    await user.type(name, '  New Co ');
    await user.upload(screen.getByLabelText('Icon file'), png());
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(put).toHaveBeenCalled());
    const [url, body] = put.mock.calls[0] as [string, FormData];
    expect(url).toBe('/settings/branding');
    expect(body).toBeInstanceOf(FormData);
    expect(body.get('name')).toBe('New Co');
    expect((body.get('icon') as File).name).toBe('logo.png');
    await waitFor(() => expect((api.get as unknown as ReturnType<typeof vi.fn>).mock.calls.length).toBeGreaterThan(1)); // invalidated -> refetched
  });

  it('maps a 422 field error onto the name input', async () => {
    mockBranding({ name: 'Old Co', hasIcon: false, iconVersion: 0 });
    vi.spyOn(api, 'put').mockRejectedValue(Object.assign(new Error('x'), {
      isAxiosError: true, response: { status: 422, data: { message: 'Validation failed', fieldErrors: [{ field: 'name', message: 'Business name must not contain control characters' }] } },
    }));
    const user = userEvent.setup();
    wrap(<BusinessSettingsPage />);
    await waitFor(() => expect(screen.getByLabelText('Business name')).toHaveValue('Old Co'));
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Business name must not contain control characters')).toBeInTheDocument();
  });

  it('removes the current icon through the API', async () => {
    mockBranding({ name: 'Old Co', hasIcon: true, iconVersion: 5 });
    const del = vi.spyOn(api, 'delete').mockResolvedValue({} as never);
    const user = userEvent.setup();
    wrap(<BusinessSettingsPage />);
    expect(await screen.findByAltText('Current icon')).toHaveAttribute('src', '/api/v1/settings/branding/icon?v=5');
    await user.click(screen.getByRole('button', { name: /Remove icon/ }));
    await waitFor(() => expect(del).toHaveBeenCalledWith('/settings/branding/icon'));
  });
});

describe('permission gating', () => {
  it('RequirePermission sends users without SETTINGS.MANAGE to /403', async () => {
    permissions = [];
    vi.resetModules();
    const { can } = { can: (p: string) => permissions.includes(p) };
    expect(can('SETTINGS.MANAGE')).toBe(false);
    expect(MENU.filter((m) => m.group === 'admin' && can(m.permission)).map((m) => m.path)).not.toContain('/business-settings');
  });
});
