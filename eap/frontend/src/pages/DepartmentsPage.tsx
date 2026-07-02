import React, { useState } from 'react';
import { useDepartments } from '@/hooks/useDepartments';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { Modal } from '@/components/Modal';
import { Input } from '@/components/Input';
import { useToast } from '@/components/Toast';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Building2, Plus, Search, Trash2, Edit2, Eye, Info } from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { Department } from '@/types';
import { ConfirmDialog } from '@/components/ConfirmDialog';

const deptSchema = z.object({
  code: z.string()
    .min(2, 'Mã phòng ban phải từ 2 ký tự trở lên')
    .max(10, 'Mã phòng ban không dài quá 10 ký tự')
    .toUpperCase(),
  name: z.string().min(2, 'Tên phòng ban không được để trống'),
  description: z.string().optional(),
});

type DeptSchema = z.infer<typeof deptSchema>;

export const DepartmentsPage: React.FC = () => {
  const { showToast } = useToast();
  const {
    departments,
    isLoading,
    createDepartment,
    isCreating,
    editDepartment,
    isEditing,
    deleteDepartment,
    isDeleting,
  } = useDepartments();

  // Filters & State
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const itemsPerPage = 5;

  // Modal control
  const [modalMode, setModalMode] = useState<'CREATE' | 'EDIT' | 'DETAIL' | 'DELETE_CONFIRM' | null>(null);
  const [selectedDept, setSelectedDept] = useState<Department | null>(null);

  // Forms
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<DeptSchema>({
    resolver: zodResolver(deptSchema),
  });

  const handleOpenCreate = () => {
    reset({ code: '', name: '', description: '' });
    setModalMode('CREATE');
  };

  const handleOpenEdit = (dept: Department) => {
    setSelectedDept(dept);
    setValue('code', dept.code);
    setValue('name', dept.name);
    setValue('description', dept.description || '');
    setModalMode('EDIT');
  };

  const handleOpenDetail = (dept: Department) => {
    setSelectedDept(dept);
    setModalMode('DETAIL');
  };

  const handleOpenDelete = (dept: Department) => {
    setSelectedDept(dept);
    setModalMode('DELETE_CONFIRM');
  };

  const onSubmit = async (values: DeptSchema) => {
    try {
      if (modalMode === 'CREATE') {
        await createDepartment({
          code: values.code,
          name: values.name,
        });
        showToast('Tạo phòng ban nghiệp vụ thành công!', 'success');
      } else if (modalMode === 'EDIT' && selectedDept) {
        await editDepartment(selectedDept.id, values.name, values.code, values.description || '');
        showToast('Cập nhật thông tin phòng ban thành công!', 'success');
      }
      setModalMode(null);
      reset();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.', 'error');
    }
  };

  const executeDelete = async () => {
    if (selectedDept) {
      try {
        await deleteDepartment(selectedDept.id);
        showToast('Đã xóa mềm phòng ban thành công.', 'success');
      } catch (err: any) {
        showToast(err.response?.data?.message || 'Không thể xóa phòng ban.', 'error');
      } finally {
        setModalMode(null);
      }
    }
  };

  // Processing lists
  const filteredDepts = departments.filter((dept) => {
    const matchSearch =
      dept.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      dept.code.toLowerCase().includes(searchQuery.toLowerCase());
    return matchSearch;
  });

  // Pagination
  const totalPages = Math.ceil(filteredDepts.length / itemsPerPage) || 1;
  const paginatedDepts = filteredDepts.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  return (
    <div className="flex flex-col gap-6">
      {/* Search and control header */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        <div className="relative w-full sm:max-w-xs">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Tìm kiếm phòng ban..."
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
            className="h-10 w-full rounded-xl border border-input bg-card pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        <Button onClick={handleOpenCreate} className="h-10 shrink-0">
          <Plus className="w-4 h-4 mr-2" />
          Tạo phòng ban
        </Button>
      </div>

      {/* Main Table View */}
      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="p-6 flex flex-col gap-4">
            <Skeleton className="h-10 w-full animate-pulse" />
            <Skeleton className="h-12 w-full animate-pulse" />
          </div>
        ) : paginatedDepts.length === 0 ? (
          <div className="p-16 flex flex-col items-center justify-center text-center gap-4">
            <div className="p-4 bg-slate-100 dark:bg-slate-900 rounded-full text-muted-foreground">
              <Building2 className="w-12 h-12" />
            </div>
            <div>
              <h4 className="font-bold text-sm">Không tìm thấy dữ liệu phòng ban</h4>
              <p className="text-xs text-muted-foreground mt-1">Chưa có bản ghi hoạt động nào.</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-border bg-slate-50 dark:bg-slate-900/40 text-muted-foreground font-semibold">
                  <th className="px-6 py-4">Mã</th>
                  <th className="px-6 py-4">Tên phòng ban</th>
                  <th className="px-6 py-4">Mô tả</th>
                  <th className="px-6 py-4">Ngày khởi tạo</th>
                  <th className="px-6 py-4 text-right pr-8">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {paginatedDepts.map((dept) => (
                  <tr key={dept.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors">
                    <td className="px-6 py-4 font-mono font-bold text-primary">{dept.code}</td>
                    <td className="px-6 py-4 font-semibold text-slate-800 dark:text-slate-200">{dept.name}</td>
                    <td className="px-6 py-4 text-muted-foreground max-w-xs truncate">{dept.description}</td>
                    <td className="px-6 py-4 text-muted-foreground">
                      {dept.createdAt ? new Date(dept.createdAt).toLocaleDateString('vi-VN') : 'N/A'}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenDetail(dept)}
                          className="h-8 rounded-xl gap-1.5 text-slate-700 dark:text-slate-300 hover:bg-slate-100"
                          title="Xem chi tiết"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Chi tiết</span>
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenEdit(dept)}
                          className="h-8 rounded-xl gap-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 dark:hover:bg-blue-950/15"
                          title="Sửa phòng ban"
                        >
                          <Edit2 className="w-3.5 h-3.5" />
                          <span>Sửa</span>
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenDelete(dept)}
                          className="h-8 rounded-xl gap-1.5 text-rose-600 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/15"
                          title="Xóa mềm"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                          <span>Xóa</span>
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Pagination controls */}
      {totalPages > 1 && (
        <div className="flex justify-end items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
            disabled={currentPage === 1}
          >
            Trước
          </Button>
          <span className="text-xs text-muted-foreground font-semibold">
            Trang {currentPage} / {totalPages}
          </span>
          <Button
            variant="outline"
            size="sm"
            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
            disabled={currentPage === totalPages}
          >
            Sau
          </Button>
        </div>
      )}

      {/* Create/Edit Modal */}
      <Modal
        isOpen={modalMode === 'CREATE' || modalMode === 'EDIT'}
        onClose={() => setModalMode(null)}
        title={modalMode === 'CREATE' ? 'Tạo phòng ban nghiệp vụ mới' : 'Cập nhật thông tin phòng ban'}
      >
        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <Input
            label="Mã phòng ban (viết hoa)"
            placeholder="Ví dụ: RND, HR..."
            error={errors.code?.message}
            {...register('code')}
          />
          <Input
            label="Tên phòng ban"
            placeholder="Nhập tên gọi chính thức..."
            error={errors.name?.message}
            {...register('name')}
          />
          
          {modalMode === 'EDIT' && (
            <div className="flex flex-col gap-1">
              <label className="text-[10px] font-bold text-muted-foreground uppercase">Mô tả phòng ban</label>
              <textarea
                placeholder="Nhập thông tin mô tả..."
                className="w-full rounded-xl border border-input p-3 text-xs bg-background focus:outline-none focus:ring-2 focus:ring-ring min-h-[80px]"
                {...register('description')}
              />
            </div>
          )}

          <div className="flex justify-end gap-3 mt-2">
            <Button type="button" variant="outline" onClick={() => setModalMode(null)}>
              Hủy
            </Button>
            <Button type="submit" loading={isCreating || isEditing}>
              Lưu lại
            </Button>
          </div>
        </form>
      </Modal>

      {/* Details View Modal */}
      <Modal
        isOpen={modalMode === 'DETAIL'}
        onClose={() => setModalMode(null)}
        title={`Thông tin chi tiết: ${selectedDept?.name}`}
      >
        <div className="flex flex-col gap-4 text-xs">
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Mã phòng ban:</span>
              <span className="font-mono font-bold text-primary text-sm">{selectedDept?.code}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Trạng thái:</span>
              <span className="font-semibold text-emerald-500">Đang hoạt động</span>
            </div>
          </div>

          <div className="flex flex-col gap-0.5">
            <span className="text-muted-foreground font-medium">Mô tả chi tiết:</span>
            <p className="bg-slate-50 dark:bg-slate-900 p-3 rounded-xl border border-border leading-relaxed text-slate-600 dark:text-slate-400">
              {selectedDept?.description || 'Phòng ban nghiệp vụ chuyên trách trong hệ thống EAP.'}
            </p>
          </div>

          <div className="grid grid-cols-2 gap-4 pt-2 border-t border-border">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Thời gian khởi tạo:</span>
              <span className="font-semibold">{selectedDept?.createdAt ? new Date(selectedDept.createdAt).toLocaleString('vi-VN') : 'N/A'}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Cập nhật cuối:</span>
              <span className="font-semibold">{selectedDept?.updatedAt ? new Date(selectedDept.updatedAt).toLocaleString('vi-VN') : 'N/A'}</span>
            </div>
          </div>

          <div className="flex justify-end mt-4">
            <Button onClick={() => setModalMode(null)}>Đóng</Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={modalMode === 'DELETE_CONFIRM'}
        onClose={() => setModalMode(null)}
        onConfirm={executeDelete}
        title="Xác nhận xóa phòng ban"
        message="Bạn có chắc chắn muốn xoá mục này? Hành động này không thể hoàn tác."
        confirmLabel="Xoá"
        isLoading={isDeleting}
        variant="danger"
      />
    </div>
  );
};
