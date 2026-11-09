import { RouterProvider } from 'react-router-dom';
import { ConfigProvider, theme } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '../auth/AuthContext';
import { BrandingHead } from '../components/common/BrandingHead';
import { router } from './router';

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });

export function App() {
  return (
    <ConfigProvider theme={{ algorithm: theme.defaultAlgorithm, token: { controlHeight: 44 } }}> {/* light theme only */}
      <QueryClientProvider client={queryClient}>
        <BrandingHead />
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </QueryClientProvider>
    </ConfigProvider>
  );
}
