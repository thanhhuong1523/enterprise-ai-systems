import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { documentService } from '@/services/api';
import { Document } from '@/types';

export const useDocuments = (page = 0, size = 10) => {
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['original-documents', page],
    queryFn: () => documentService.listOriginalDocuments(page, size),
  });

  const rawDocs = data?.data?.content || [];
  const totalElements = data?.data?.totalElements || 0;

  const documents: Document[] = rawDocs.map((doc) => ({
    ...doc,
    status: 'ACTIVE',
  }));

  const uploadMutation = useMutation({
    mutationFn: ({ title, file }: { title: string; file: File }) =>
      documentService.uploadOriginalDocument(title, file),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['original-documents'] });
    },
  });

  const editMutation = useMutation({
    mutationFn: ({ id, title }: { id: string; title: string }) =>
      documentService.updateOriginalDocument(id, { title }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['original-documents'] });
    },
  });

  const deleteDocMutation = useMutation({
    mutationFn: documentService.deleteOriginalDocument,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['original-documents'] });
    },
  });

  const editDocument = async (id: string, title: string) => {
    await editMutation.mutateAsync({ id, title });
  };

  const deleteDocument = async (id: string) => {
    await deleteDocMutation.mutateAsync(id);
  };

  return {
    documents,
    totalElements,
    isLoading,
    error,
    uploadDocument: uploadMutation.mutateAsync,
    isUploading: uploadMutation.isPending,
    deleteDocument,
    isDeleting: deleteDocMutation.isPending,
    editDocument,
    isEditing: editMutation.isPending,
  };
};
