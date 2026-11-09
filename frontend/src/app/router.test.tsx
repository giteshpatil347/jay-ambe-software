import { lazy } from 'react';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { MainLayout } from '../layout/MainLayout';

vi.mock('../auth/useAuth', () => ({ useAuth: () => ({ user: { fullName: 'T' }, logout: vi.fn() }) }));
vi.mock('../auth/usePermission', () => ({ usePermission: () => ({ can: () => true }) }));

it('shows the Spin fallback inside the layout, then the lazy page', async () => {
  const Lazy = lazy(() => new Promise<{ default: () => JSX.Element }>((r) => setTimeout(() => r({ default: () => <p>lazy page ready</p> }), 20)));
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={['/x']}>
      <Routes><Route element={<MainLayout />}><Route path="x" element={<Lazy />} /></Route></Routes>
    </MemoryRouter></QueryClientProvider>,
  );
  expect(screen.getByRole('status')).toBeInTheDocument();
  expect(await screen.findByText('lazy page ready')).toBeInTheDocument();
  expect(screen.queryByRole('status')).not.toBeInTheDocument();
});
