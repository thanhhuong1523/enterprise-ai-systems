import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { userService } from '@/services/api';
import { User } from '@/types';

export const useUsers = () => {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['users'],
    queryFn: userService.listUsers,
  });

  const users: User[] = (data?.data || []).map((user) => {
    const avatarUrl = `https://ui-avatars.com/api/?name=${encodeURIComponent(user.username)}&background=0D8ABC&color=fff&rounded=true`;
    return {
      ...user,
      avatar: avatarUrl,
      createdAt: user.createdAt || new Date('2026-06-26T00:00:00Z').toISOString(),
      status: 'ACTIVE',
    };
  });

  const createMutation = useMutation({
    mutationFn: userService.createUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const editMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: any }) =>
      userService.updateUser(id, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: userService.deleteUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
    },
  });

  const editUser = async (id: string, username: string, email: string, fullName?: string, phone?: string) => {
    await editMutation.mutateAsync({
      id,
      payload: { username, email, fullName, phone },
    });
  };

  const deleteUser = async (id: string) => {
    await deleteMutation.mutateAsync(id);
  };

  return {
    users,
    isLoading,
    error,
    createUser: createMutation.mutateAsync,
    isCreating: createMutation.isPending,
    editUser,
    isEditing: editMutation.isPending,
    deleteUser,
    isDeleting: deleteMutation.isPending,
  };
};
