import { lazy } from 'react';
import type { ReactElement } from 'react';
import { Navigate, createBrowserRouter } from 'react-router-dom';
import { RequirePermission } from '../auth/RequirePermission';
import { MainLayout } from '../layout/MainLayout';
import { PlaceholderPage } from '../components/common/PlaceholderPage';
import { LoginPage } from '../pages/LoginPage';
import { ChangePasswordPage } from '../pages/auth/ChangePasswordPage';
import { ForbiddenPage, NotFoundPage } from '../pages/ErrorPages';
import { MENU } from './menu';

// Route-level code splitting: each page is its own chunk, loaded on first visit (Suspense lives in MainLayout).
const ProductsPage = lazy(() => import('../pages/products/ProductsPage').then((m) => ({ default: m.ProductsPage })));
const PartiesPage = lazy(() => import('../pages/suppliers/PartiesPage').then((m) => ({ default: m.PartiesPage })));
const UsersPage = lazy(() => import('../pages/users/UsersPage').then((m) => ({ default: m.UsersPage })));
const RolesPage = lazy(() => import('../pages/roles/RolesPage').then((m) => ({ default: m.RolesPage })));
const PermissionMatrix = lazy(() => import('../pages/permissions/PermissionMatrix').then((m) => ({ default: m.PermissionMatrix })));
const ExpensesPage = lazy(() => import('../pages/expenses/ExpensesPage'));
const ExpenseCategoriesPage = lazy(() => import('../pages/expense-categories/ExpenseCategoriesPage'));
const EmployeesPage = lazy(() => import('../pages/employees/EmployeesPage'));
const PaymentsPage = lazy(() => import('../pages/payments/PaymentsPage'));
const AuditLogsPage = lazy(() => import('../pages/audit-logs/AuditLogsPage'));
const PartyLedgerPage = lazy(() => import('../pages/suppliers/PartyLedgerPage'));
const MaterialInPage = lazy(() => import('../pages/material-in/MaterialInPage'));
const MaterialOutPage = lazy(() => import('../pages/material-out/MaterialOutPage'));
const InventoryPage = lazy(() => import('../pages/inventory/InventoryPage'));
const ReportsPage = lazy(() => import('../pages/reports/ReportsPage'));
const BusinessSettingsPage = lazy(() => import('../pages/business-settings/BusinessSettingsPage'));
const DashboardPage = lazy(() => import('../pages/dashboard/DashboardPage'));

/** Implemented pages by menu path; anything missing falls back to the placeholder. Append-only. */
const PAGES: Record<string, ReactElement> = {
  '/products': <ProductsPage />,
  '/suppliers': <PartiesPage />,
  '/users': <UsersPage />,
  '/roles': <RolesPage />,
  '/permissions': <PermissionMatrix />,
  '/expenses': <ExpensesPage />,
  '/expense-categories': <ExpenseCategoriesPage />,
  '/employees': <EmployeesPage />,
  '/payments': <PaymentsPage />,
  '/audit-logs': <AuditLogsPage />,
  '/material-in': <MaterialInPage />,
  '/material-out': <MaterialOutPage />,
  '/inventory': <InventoryPage />,
  '/reports': <ReportsPage />,
  '/business-settings': <BusinessSettingsPage />,
  '/dashboard': <DashboardPage />,
};

export const routes = [
  { path: '/login', element: <LoginPage /> },
  {
    path: '/',
    element: <RequirePermission><MainLayout /></RequirePermission>,
    children: [
      { index: true, element: <Navigate to="/dashboard" replace /> },
      ...MENU.map((m) => ({
        path: m.path.slice(1),
        element: <RequirePermission permission={m.permission}>{PAGES[m.path] ?? <PlaceholderPage title={m.label} />}</RequirePermission>,
      })),
      { path: 'suppliers/:id/ledger', element: <RequirePermission permission="PARTY.VIEW"><PartyLedgerPage /></RequirePermission> },
      { path: 'change-password', element: <ChangePasswordPage /> },
      { path: '403', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
];

export const router = createBrowserRouter(routes);
