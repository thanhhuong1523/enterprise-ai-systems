import { apiClient } from '@/api/client';
import { ApiResponse, Department, Document, LoginResponse, User } from '@/types';

export const authService = {
  login: async (payload: any): Promise<ApiResponse<LoginResponse>> => {
    const response = await apiClient.post<ApiResponse<LoginResponse>>('/api/v1/auth/login', payload);
    return response.data;
  },
};

export const departmentService = {
  createDepartment: async (payload: { code: string; name: string }): Promise<ApiResponse<Department>> => {
    const response = await apiClient.post<ApiResponse<Department>>('/api/v1/departments', payload);
    return response.data;
  },
  listDepartments: async (): Promise<ApiResponse<Department[]>> => {
    const response = await apiClient.get<ApiResponse<Department[]>>('/api/v1/departments');
    return response.data;
  },
  getDepartmentDetail: async (id: string): Promise<ApiResponse<Department>> => {
    const response = await apiClient.get<ApiResponse<Department>>(`/api/v1/departments/${id}`);
    return response.data;
  },
  updateDepartment: async (id: string, payload: { code?: string; name?: string; description?: string }): Promise<ApiResponse<Department>> => {
    const response = await apiClient.put<ApiResponse<Department>>(`/api/v1/departments/${id}`, payload);
    return response.data;
  },
  deleteDepartment: async (id: string): Promise<ApiResponse<void>> => {
    const response = await apiClient.delete<ApiResponse<void>>(`/api/v1/departments/${id}`);
    return response.data;
  },
};

export const userService = {
  createUser: async (payload: any): Promise<ApiResponse<User>> => {
    const response = await apiClient.post<ApiResponse<User>>('/api/v1/users', payload);
    return response.data;
  },
  listUsers: async (): Promise<ApiResponse<User[]>> => {
    const response = await apiClient.get<ApiResponse<User[]>>('/api/v1/users');
    return response.data;
  },
  getUserDetail: async (id: string): Promise<ApiResponse<User>> => {
    const response = await apiClient.get<ApiResponse<User>>(`/api/v1/users/${id}`);
    return response.data;
  },
  updateUser: async (id: string, payload: any): Promise<ApiResponse<User>> => {
    const response = await apiClient.put<ApiResponse<User>>(`/api/v1/users/${id}`, payload);
    return response.data;
  },
  deleteUser: async (id: string): Promise<ApiResponse<void>> => {
    const response = await apiClient.delete<ApiResponse<void>>(`/api/v1/users/${id}`);
    return response.data;
  },
};

export interface PageResponse<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  size: number;
  number: number;
}

export const documentService = {
  uploadOriginalDocument: async (title: string, file: File): Promise<ApiResponse<Document>> => {
    const formData = new FormData();
    formData.append('title', title);
    formData.append('file', file);
    const response = await apiClient.post<ApiResponse<Document>>('/api/v1/original-documents', formData, {
      headers: {
        'Content-Type': undefined,
      },
    });
    return response.data;
  },
  listOriginalDocuments: async (page = 0, size = 10): Promise<ApiResponse<PageResponse<Document>>> => {
    const response = await apiClient.get<ApiResponse<PageResponse<Document>>>(`/api/v1/original-documents?page=${page}&size=${size}`);
    return response.data;
  },
  getOriginalDocumentDetail: async (id: string): Promise<ApiResponse<Document>> => {
    const response = await apiClient.get<ApiResponse<Document>>(`/api/v1/original-documents/${id}`);
    return response.data;
  },
  updateOriginalDocument: async (id: string, payload: { title: string }): Promise<ApiResponse<Document>> => {
    const response = await apiClient.put<ApiResponse<Document>>(`/api/v1/original-documents/${id}`, payload);
    return response.data;
  },
  deleteOriginalDocument: async (id: string): Promise<ApiResponse<void>> => {
    const response = await apiClient.delete<ApiResponse<void>>(`/api/v1/original-documents/${id}`);
    return response.data;
  },
  createAlias: async (payload: { originalDocumentId: string; aliasDepartmentId: string }): Promise<ApiResponse<Document>> => {
    const response = await apiClient.post<ApiResponse<Document>>('/api/v1/alias-documents', payload);
    return response.data;
  },
  listSharedDocuments: async (page = 0, size = 10): Promise<ApiResponse<PageResponse<Document>>> => {
    const response = await apiClient.get<ApiResponse<PageResponse<Document>>>(`/api/v1/alias-documents?page=${page}&size=${size}`);
    return response.data;
  },
  listDocumentAliases: async (id: string): Promise<ApiResponse<Document[]>> => {
    const response = await apiClient.get<ApiResponse<Document[]>>(`/api/v1/original-documents/${id}/aliases`);
    return response.data;
  },
  resolveAlias: async (id: string): Promise<Blob> => {
    const response = await apiClient.get(`/api/v1/alias-documents/${id}`, {
      responseType: 'blob',
    });
    return response.data;
  },
  deleteAlias: async (id: string): Promise<ApiResponse<void>> => {
    const response = await apiClient.delete<ApiResponse<void>>(`/api/v1/alias-documents/${id}`);
    return response.data;
  },
};
