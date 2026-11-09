import { Suspense, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Button, Drawer, Dropdown, Grid, Layout, Menu, Spin, Typography } from 'antd';
import type { MenuProps } from 'antd';
import { DownOutlined, EllipsisOutlined, LogoutOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons';
import { MENU } from '../app/menu';
import { useAuth } from '../auth/useAuth';
import { usePermission } from '../auth/usePermission';
import { BrandMark } from '../components/common/BrandMark';

const BAR_H = 56;
const TOP_H = 48;
const PAGE_FALLBACK = <div role="status" style={{ display: 'flex', justifyContent: 'center', padding: 48 }}><Spin /></div>;
const TAP = { minWidth: 44, minHeight: 44 };

export function MainLayout() {
  const { user, logout } = useAuth();
  const { can } = usePermission();
  const screens = Grid.useBreakpoint();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [more, setMore] = useState(false);

  const allowed = MENU.filter((m) => can(m.permission));
  const items = allowed.filter((m) => (m.group ?? 'main') === 'main'); // daily work: top bar / tab bar
  const master = allowed.filter((m) => m.group === 'master');
  const admin = allowed.filter((m) => m.group === 'admin');
  const active = allowed.find((m) => pathname.startsWith(m.path))?.path;
  const pageTitle = allowed.find((m) => m.path === active)?.label;
  const inSettings = [...master, ...admin].some((m) => m.path === active);
  const signOut = () => logout().then(() => navigate('/login', { replace: true }));
  const menuItems = (list: typeof items) => list.map((m) => ({ key: m.path, icon: m.icon, label: <Link to={m.path}>{m.label}</Link> }));

  // Profile menu: Settings (master-data setup + administration) and Sign out.
  const group = (label: string, list: typeof items) => list.length === 0 ? [] : [{
    type: 'group' as const, label, children: list.map((m) => ({ key: m.path, icon: m.icon, label: m.label })),
  }];
  const profileItems: MenuProps['items'] = [
    ...group('Settings · Master data', master),
    ...group('Settings · Administration', admin),
    ...(master.length + admin.length > 0 ? [{ type: 'divider' as const }] : []),
    { key: 'signout', icon: <LogoutOutlined />, label: 'Sign out', danger: true },
  ];
  const profile = (compact: boolean) => (
    <Dropdown trigger={['click']} placement="bottomRight"
      menu={{ items: profileItems, selectedKeys: inSettings && active ? [active] : [], onClick: ({ key }) => (key === 'signout' ? signOut() : navigate(key)) }}>
      <Button type="text" aria-label="Profile and settings" title="Profile and settings" icon={<UserOutlined />}
        style={{ ...(compact ? TAP : {}), color: inSettings ? '#1677ff' : undefined }}>
        {!compact && <>{user?.fullName} <SettingOutlined /> <DownOutlined style={{ fontSize: 10 }} /></>}
      </Button>
    </Dropdown>
  );

  if (screens.lg) {
    return (
      <Layout style={{ minHeight: '100dvh' }}>
        {/* Compact top navigation: items that do not fit fold into the trailing "..." menu automatically. */}
        <Layout.Header style={{
          position: 'sticky', top: 0, zIndex: 10, height: TOP_H, lineHeight: `${TOP_H}px`, padding: '0 16px', background: '#fff',
          borderBottom: '1px solid #f0f0f0', display: 'flex', alignItems: 'center', gap: 12,
        }}>
          <span style={{ whiteSpace: 'nowrap', maxWidth: 260, display: 'inline-flex' }}><BrandMark /></span>
          <Menu mode="horizontal" selectedKeys={active ? [active] : []} items={menuItems(items)} overflowedIndicator={<EllipsisOutlined />}
            style={{ flex: 1, minWidth: 0, borderBottom: 0, lineHeight: `${TOP_H - 2}px`, fontSize: 13 }} />
          {profile(false)}
        </Layout.Header>
        <Layout.Content style={{ padding: 16, minWidth: 0 }}><Suspense fallback={PAGE_FALLBACK}><Outlet /></Suspense></Layout.Content>
      </Layout>
    );
  }

  const tabs = items.length > 5 ? items.slice(0, 4) : items;
  const rest = items.length > 5 ? items.slice(4) : [];
  const tabStyle = (on: boolean) => ({
    ...TAP, flex: 1, display: 'flex', flexDirection: 'column' as const, alignItems: 'center', justifyContent: 'center',
    fontSize: 11, color: on ? '#1677ff' : 'rgba(0,0,0,.65)', background: 'none', border: 0,
  });

  return (
    <Layout style={{ minHeight: '100dvh', overflowX: 'hidden' }}>
      <Layout.Header style={{
        background: '#fff', height: BAR_H, lineHeight: 'normal', padding: '0 12px', paddingTop: 'env(safe-area-inset-top)',
        boxSizing: 'content-box', display: 'flex', alignItems: 'center', justifyContent: 'space-between', position: 'sticky', top: 0, zIndex: 10,
      }}>
        {pageTitle ? <Typography.Text strong>{pageTitle}</Typography.Text> : <BrandMark />}
        {profile(true)}
      </Layout.Header>
      <Layout.Content style={{ padding: 12, paddingBottom: BAR_H + 24, minWidth: 0 }}><Suspense fallback={PAGE_FALLBACK}><Outlet /></Suspense></Layout.Content>
      <nav aria-label="Primary" style={{
        position: 'fixed', bottom: 0, left: 0, right: 0, display: 'flex', background: '#fff', borderTop: '1px solid #f0f0f0',
        paddingBottom: 'env(safe-area-inset-bottom)', paddingLeft: 'env(safe-area-inset-left)', paddingRight: 'env(safe-area-inset-right)', zIndex: 10,
      }}>
        {tabs.map((m) => (
          <Link key={m.path} to={m.path} style={tabStyle(m.path === active)}>
            <span style={{ fontSize: 18 }}>{m.icon}</span>{m.label}
          </Link>
        ))}
        {rest.length > 0 && (
          <button type="button" onClick={() => setMore(true)} style={tabStyle(false)}>
            <span style={{ fontSize: 18 }}><EllipsisOutlined /></span>More
          </button>
        )}
      </nav>
      <Drawer title="More" placement="bottom" open={more} onClose={() => setMore(false)} height="auto">
        <Menu mode="vertical" selectedKeys={active ? [active] : []} items={menuItems(rest)}
          onClick={() => setMore(false)} style={{ border: 0 }} />
      </Drawer>
    </Layout>
  );
}
