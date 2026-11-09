import type { OverrideEffect, PermissionOverride } from '../../types/masterData';

export type Choice = 'INHERIT' | OverrideEffect;
export type MatrixState = Record<string, Choice>;

export const stateFromOverrides = (overrides: PermissionOverride[]): MatrixState =>
  Object.fromEntries(overrides.map((o) => [o.permissionCode, o.effect]));

/** Bare array for PUT /users/{id}/permissions; INHERIT entries are omitted. */
export function buildOverridePayload(state: MatrixState): PermissionOverride[] {
  return Object.entries(state)
    .filter((e): e is [string, OverrideEffect] => e[1] === 'ALLOW' || e[1] === 'DENY')
    .map(([permissionCode, effect]) => ({ permissionCode, effect }));
}
