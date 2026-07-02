export type Role = 'SYSTEM_ADMIN' | 'ROLE_BOARD' | 'ROLE_EMPLOYEE' | 'ROLE_DEPT_MANAGER';

export interface Department {
  id: string;
  code: string;
  name: string;
  description?: string;
  createdAt?: string;
  updatedAt?: string | null;
  deletedAt?: string | null;
  status?: 'ACTIVE' | 'DELETED';
}

export interface User {
  id: string;
  username: string;
  email: string;
  role: Role;
  departmentId: string | null;
  avatar?: string;
  status?: 'ACTIVE' | 'DELETED';
  fullName?: string | null;
  phone?: string | null;
  createdAt?: string;
  updatedAt?: string | null;
  deletedAt?: string | null;
}

export interface Document {
  id: string;
  businessCode: string;
  title: string;
  fileReference: string | null;
  fileSize: number | null;
  hash: string | null;
  ownerDepartmentId: string;
  parentId: string | null;
  creatorDepartmentId: string | null;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string | null;
  deletedAt: string | null;
  status?: 'ACTIVE' | 'DELETED';
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  refreshToken: string;
  refreshTokenExpiresIn: number;
  userInfo: User;
}

export interface ApiResponse<T> {
  success: boolean;
  code: string;
  timestamp: string;
  data: T;
  message?: string;
}
