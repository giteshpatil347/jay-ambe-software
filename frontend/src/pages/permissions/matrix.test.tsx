import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { userService } from '../../services/userService';
import type { User } from '../../types/masterData';
import { buildOverridePayload, stateFromOverrides } from './matrix';
import { MatrixEditor } from './PermissionMatrix';

describe('matrix mapping', () => {
  it('omits INHERIT and keeps ALLOW/DENY as a bare array', () => {
    expect(buildOverridePayload({ 'A.VIEW': 'ALLOW', 'B.VIEW': 'INHERIT', 'C.VIEW': 'DENY' })).toEqual([
      { permissionCode: 'A.VIEW', effect: 'ALLOW' }, { permissionCode: 'C.VIEW', effect: 'DENY' },
    ]);
    expect(stateFromOverrides([{ permissionCode: 'A.VIEW', effect: 'DENY' }])).toEqual({ 'A.VIEW': 'DENY' });
  });
});

describe('MatrixEditor', () => {
  it('PUTs the changed overrides', async () => {
    const user = { id: 5, roles: ['VIEWER'], permissionOverrides: [{ permissionCode: 'B.VIEW', effect: 'DENY' }] } as User;
    const spy = vi.spyOn(userService, 'setOverrides').mockResolvedValue({ ...user });
    const catalogue = [{ moduleCode: 'A', permissions: [{ permissionCode: 'A.VIEW', actionCode: 'VIEW', description: null }] },
      { moduleCode: 'B', permissions: [{ permissionCode: 'B.VIEW', actionCode: 'VIEW', description: null }] }];
    render(
      <QueryClientProvider client={new QueryClient()}>
        <MatrixEditor user={user} catalogue={catalogue} roleGranted={new Set(['A.VIEW'])} readOnly={false} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByText('A'));
    await userEvent.click(screen.getByText('Allow'));
    await userEvent.click(screen.getByRole('button', { name: /Save overrides/ }));
    await waitFor(() => expect(spy).toHaveBeenCalledWith(5, [{ permissionCode: 'B.VIEW', effect: 'DENY' }, { permissionCode: 'A.VIEW', effect: 'ALLOW' }]));
  });
});
