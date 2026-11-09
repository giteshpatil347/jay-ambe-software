export interface AuthUser {
  id: number | string;
  fullName: string;
  permissions: string[];
  passwordChangeRequired: boolean;
}
export interface AuthResponse {
  accessToken: string;
  expiresIn: number;
  user: AuthUser;
}
export interface ProblemDetail {
  code?: string;
  message?: string;
  fieldErrors?: { field: string; message: string }[];
}
