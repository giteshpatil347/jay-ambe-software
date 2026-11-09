import {
  AppstoreOutlined, AuditOutlined, BarChartOutlined, DashboardOutlined, DollarOutlined, DownloadOutlined,
  InboxOutlined, LockOutlined, SettingOutlined, ShopOutlined, TagsOutlined, TeamOutlined, UploadOutlined, UserOutlined, WalletOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

/** main = daily work (top bar / tab bar); master = master-data setup and admin = administration (both in the profile menu's Settings). */
export type MenuGroup = 'main' | 'master' | 'admin';
export interface MenuItem { path: string; label: string; permission: string; icon: ReactNode; group?: MenuGroup }

/** Single source for routes, top bar, bottom tab bar, "More" drawer and the profile Settings menu. Order = priority on mobile. */
export const MENU: MenuItem[] = [
  { path: '/dashboard', label: 'Dashboard', permission: 'DASHBOARD.VIEW', icon: <DashboardOutlined /> },
  { path: '/material-in', label: 'Material In', permission: 'MATERIAL_IN.VIEW', icon: <DownloadOutlined /> },
  { path: '/material-out', label: 'Material Out', permission: 'MATERIAL_OUT.VIEW', icon: <UploadOutlined /> },
  { path: '/inventory', label: 'Inventory', permission: 'INVENTORY.VIEW', icon: <InboxOutlined /> },
  { path: '/products', label: 'Materials', permission: 'PRODUCT.VIEW', icon: <AppstoreOutlined />, group: 'master' },
  { path: '/suppliers', label: 'Suppliers', permission: 'PARTY.VIEW', icon: <ShopOutlined />, group: 'master' },
  { path: '/expenses', label: 'Expenses', permission: 'EXPENSE.VIEW', icon: <DollarOutlined /> },
  { path: '/payments', label: 'Payments', permission: 'PAYMENT.VIEW', icon: <WalletOutlined /> },
  { path: '/expense-categories', label: 'Expense Categories', permission: 'EXPENSE_CATEGORY.MANAGE', icon: <TagsOutlined />, group: 'master' },
  { path: '/employees', label: 'Employees', permission: 'EMPLOYEE.VIEW', icon: <TeamOutlined />, group: 'master' },
  { path: '/reports', label: 'Reports', permission: 'REPORT.VIEW', icon: <BarChartOutlined /> },
  { path: '/users', label: 'Users', permission: 'USER.VIEW', icon: <UserOutlined />, group: 'admin' },
  { path: '/roles', label: 'Roles', permission: 'ROLE.VIEW', icon: <TeamOutlined />, group: 'admin' },
  { path: '/permissions', label: 'Permissions', permission: 'PERMISSION.VIEW', icon: <LockOutlined />, group: 'admin' },
  { path: '/business-settings', label: 'Business settings', permission: 'SETTINGS.MANAGE', icon: <SettingOutlined />, group: 'admin' },
  { path: '/audit-logs', label: 'Audit Logs', permission: 'AUDIT.VIEW', icon: <AuditOutlined />, group: 'admin' },
];
