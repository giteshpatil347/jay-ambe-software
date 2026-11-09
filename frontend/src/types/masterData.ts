export type Status = 'ACTIVE' | 'INACTIVE';
export interface Page<T> { content: T[]; page: number; size: number; totalElements: number }

export type ProductUnit = 'TON' | 'KG' | 'PIECE';
export interface ProductCategory { id: number; name: string; status: Status }
/** Quantities are decimal STRINGS from the API. */
export interface Product {
  id: number; code: string; name: string; categoryId: number | null; categoryName: string | null;
  unit: ProductUnit; description: string | null; minimumStock: string | null; availableStock: string | null; status: Status;
}
export interface ProductInput {
  productCode?: string; name: string; categoryId?: number | null; unit: ProductUnit;
  description?: string; minimumStock?: string;
}

export type PartyType = 'SUPPLIER' | 'CLIENT' | 'BOTH';
export interface Party {
  id: number; code: string; name: string; type: PartyType; mobile: string | null; email: string | null;
  address: string | null; gstNumber: string | null; openingBalance: string | null; status: Status;
}
export interface PartyInput {
  name: string; type: PartyType; mobile?: string; email?: string; address?: string; gstNumber?: string; openingBalance?: string;
}

export type OverrideEffect = 'ALLOW' | 'DENY';
export interface PermissionOverride { permissionCode: string; effect: OverrideEffect }
export interface User {
  id: number; fullName: string; mobile: string; email: string | null; status: Status;
  passwordChangeRequired: boolean; lastLoginAt: string | null; createdAt: string;
  roles: string[]; permissionOverrides: PermissionOverride[];
}
export interface UserInput { fullName: string; mobile: string; email?: string }
export interface CreateUserInput extends UserInput { password: string; roleCodes: string[] }

export interface Role { id: number; roleCode: string; roleName: string; description: string | null; system: boolean; permissionCodes: string[] }
export interface RoleInput { roleCode?: string; roleName: string; description?: string; permissionCodes: string[] }
export interface PermissionItem { permissionCode: string; actionCode: string; description: string | null }
export interface ModuleGroup { moduleCode: string; permissions: PermissionItem[] }

/** Permission module names as shown to people: the product master is called 'material' everywhere in the app. */
export const moduleLabel = (code: string): string => (code === 'PRODUCT' ? 'MATERIAL MASTER' : code);
