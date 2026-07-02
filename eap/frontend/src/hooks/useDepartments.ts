import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { departmentService } from '@/services/api';
import { Department } from '@/types';

export const useDepartments = () => {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['departments'],
    queryFn: departmentService.listDepartments,
  });

  const departments: Department[] = (data?.data || []).map((dept) => ({
    ...dept,
    // Add fallback UI fields if null in DB
    description: dept.description || 'Phòng ban nghiệp vụ chuyên trách trong hệ thống EAP.',
    createdAt: dept.createdAt || new Date('2026-06-26T00:00:00Z').toISOString(),
    status: 'ACTIVE',
  }));

  const createMutation = useMutation({
    mutationFn: departmentService.createDepartment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });

  const editMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: { name?: string; code?: string; description?: string } }) =>
      departmentService.updateDepartment(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: departmentService.deleteDepartment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['departments'] });
    },
  });

  const editDepartment = async (id: string, name: string, code: string, description: string) => {
    await editMutation.mutateAsync({
      id,
      payload: { name, code, description },
    });
  };

  const deleteDepartment = async (id: string) => {
    await deleteMutation.mutateAsync(id);
  };

  return {
    departments,
    isLoading,
    error,
    createDepartment: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    editDepartment,
    isEditing: editMutation.isPending,
    deleteDepartment,
    isDeleting: deleteMutation.isPending,
  };
};
