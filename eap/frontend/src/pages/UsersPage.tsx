import React, { useState, useEffect } from 'react';
import { useUsers } from '@/hooks/useUsers';
import { useDepartments } from '@/hooks/useDepartments';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { Modal } from '@/components/Modal';
import { Input } from '@/components/Input';
import { Select } from '@/components/Select';
import { useToast } from '@/components/Toast';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import {
  Users,
  Plus,
  Search,
  Building2,
  Trash2,
  Edit2,
  Eye,
  User as UserIcon,
  ShieldCheck,
  Building,
  Phone
} from 'lucide-react';
import { Skeleton } from '@/components/Skeleton';
import { User, Role } from '@/types';
import { ConfirmDialog } from '@/components/ConfirmDialog';

// Form validation schema for creating a user
const createUserSchema = z.object({
  username: z.string().min(3, 'Tên đăng nhập tối thiểu 3 ký tự'),
  email: z.string().email('Định dạng email không hợp lệ'),
  password: z.string().min(6, 'Mật khẩu tối thiểu 6 ký tự'),
  confirmPassword: z.string().min(6, 'Xác nhận mật khẩu tối thiểu 6 ký tự'),
  role: z.enum(['ROLE_BOARD', 'ROLE_EMPLOYEE', 'ROLE_DEPT_MANAGER']),
  fullName: z.string().min(2, 'Họ và tên tối thiểu 2 ký tự'),
  phone: z.string().regex(/^(0[3|5|7|8|9])+([0-9]{8})$/, 'Số điện thoại Việt Nam không hợp lệ (gồm 10 số, ví dụ 0987654321)'),
}).refine((data) => data.password === data.confirmPassword, {
  message: 'Xác nhận mật khẩu không trùng khớp',
  path: ['confirmPassword'],
});

// Form validation schema for editing a user
// Note: 'role' is intentionally excluded — role is immutable after account creation
const editUserSchema = z.object({
  username: z.string().min(3, 'Tên đăng nhập tối thiểu 3 ký tự'),
  email: z.string().email('Định dạng email không hợp lệ'),
  fullName: z.string().min(2, 'Họ và tên tối thiểu 2 ký tự'),
  phone: z.string().regex(/^(0[3|5|7|8|9])+([0-9]{8})$/, 'Số điện thoại Việt Nam không hợp lệ (gồm 10 số, ví dụ 0987654321)'),
});

type CreateUserSchema = z.infer<typeof createUserSchema>;
type EditUserSchema = z.infer<typeof editUserSchema>;

export const UsersPage: React.FC = () => {
  const { showToast } = useToast();
  const { departments, isLoading: loadingDepts } = useDepartments();
  const {
    users,
    isLoading: loadingUsers,
    createUser,
    isCreating,
    editUser,
    isEditing,
    deleteUser,
    isDeleting,
  } = useUsers();

  // Active workspace states
  const [selectedDeptId, setSelectedDeptId] = useState<string | null>(null);
  
  // Table filters & pagination
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(5);

  // Modals state
  const [modalMode, setModalMode] = useState<'CREATE' | 'EDIT' | 'DETAIL' | 'DELETE_CONFIRM' | null>(null);
  const [selectedUser, setSelectedUser] = useState<User | null>(null);

  // Get active department metadata
  const activeDept = departments.find((d) => d.id === selectedDeptId);
  const isSelectedDeptBoard = activeDept?.code.toUpperCase() === 'BOARD';

  // React Hook Form for Create Mode
  const createForm = useForm<CreateUserSchema>({
    resolver: zodResolver(createUserSchema),
    defaultValues: {
      username: '',
      email: '',
      password: '',
      confirmPassword: '',
      role: 'ROLE_EMPLOYEE',
      fullName: '',
      phone: '',
    }
  });

  // React Hook Form for Edit Mode
  const editForm = useForm<EditUserSchema>({
    resolver: zodResolver(editUserSchema),
    defaultValues: {
      username: '',
      email: '',
      fullName: '',
      phone: '',
    }
  });

  // Auto-select first department when loaded
  useEffect(() => {
    if (departments.length > 0 && !selectedDeptId) {
      setSelectedDeptId(departments[0].id);
    }
  }, [departments, selectedDeptId]);

  // Adjust role dynamically based on department selection when opening form
  useEffect(() => {
    if (modalMode === 'CREATE') {
      createForm.setValue('role', isSelectedDeptBoard ? 'ROLE_BOARD' : 'ROLE_EMPLOYEE');
    }
  }, [selectedDeptId, isSelectedDeptBoard, modalMode]);

  const handleOpenCreate = () => {
    if (!selectedDeptId) {
      showToast('Vui lòng chọn phòng ban trước.', 'error');
      return;
    }
    createForm.reset({
      username: '',
      email: '',
      password: '',
      confirmPassword: '',
      role: isSelectedDeptBoard ? 'ROLE_BOARD' : 'ROLE_EMPLOYEE',
      fullName: '',
      phone: '',
    });
    setModalMode('CREATE');
  };

  const handleOpenEdit = (user: User) => {
    setSelectedUser(user);
    editForm.reset({
      username: user.username,
      email: user.email,
      fullName: user.fullName || '',
      phone: user.phone || '',
    });
    setModalMode('EDIT');
  };

  const handleOpenDetail = (user: User) => {
    setSelectedUser(user);
    setModalMode('DETAIL');
  };

  const handleOpenDelete = (user: User) => {
    setSelectedUser(user);
    setModalMode('DELETE_CONFIRM');
  };

  const onCreateSubmit = async (values: CreateUserSchema) => {
    try {
      await createUser({
        username: values.username,
        email: values.email,
        password: values.password,
        confirmPassword: values.confirmPassword,
        role: values.role,
        departmentId: selectedDeptId,
        fullName: values.fullName,
        phone: values.phone,
      });
      showToast('Tạo tài khoản người dùng thành công!', 'success');
      setModalMode(null);
      createForm.reset();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.', 'error');
    }
  };

  const onEditSubmit = async (values: EditUserSchema) => {
    if (!selectedUser) return;
    try {
      // role is NOT sent — it is immutable after account creation
      await editUser(
        selectedUser.id,
        values.username,
        values.email,
        values.fullName,
        values.phone
      );
      showToast('Cập nhật tài khoản thành công!', 'success');
      setModalMode(null);
      editForm.reset();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Có lỗi xảy ra, vui lòng thử lại.', 'error');
    }
  };

  const executeDelete = async () => {
    if (selectedUser) {
      try {
        await deleteUser(selectedUser.id);
        showToast('Đã xóa mềm tài khoản thành công.', 'success');
      } catch (err: any) {
        showToast(err.response?.data?.message || 'Không thể xóa tài khoản.', 'error');
      } finally {
        setModalMode(null);
      }
    }
  };

  // Filter right pane table users list
  const filteredUsers = users.filter((u) => {
    if (u.departmentId !== selectedDeptId) return false;
    const matchSearch =
      u.username.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.email.toLowerCase().includes(searchQuery.toLowerCase()) ||
      u.role.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (u.fullName && u.fullName.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (u.phone && u.phone.includes(searchQuery));
    return matchSearch;
  });

  const totalPages = Math.ceil(filteredUsers.length / itemsPerPage) || 1;
  const paginatedUsers = filteredUsers.slice(
    (currentPage - 1) * itemsPerPage,
    currentPage * itemsPerPage
  );

  const getDeptName = (deptId: string | null) => {
    if (!deptId) return 'Không trực thuộc';
    const dept = departments.find((d) => d.id === deptId);
    return dept ? `${dept.name} (${dept.code})` : 'N/A';
  };

  const roleOptions = isSelectedDeptBoard
    ? [{ value: 'ROLE_BOARD', label: 'Ban giám đốc (BOARD)' }]
    : [
        { value: 'ROLE_EMPLOYEE', label: 'Nhân viên nghiệp vụ (EMPLOYEE)' },
        { value: 'ROLE_DEPT_MANAGER', label: 'Trưởng phòng ban (DEPT_MANAGER)' },
      ];

  return (
    <div className="flex flex-col gap-6">
      {/* Horizontal Department Selector Bar */}
      <div className="flex flex-col gap-2">
        <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Danh sách phòng ban nghiệp vụ</span>
        {loadingDepts ? (
          <div className="flex gap-2">
            <Skeleton className="h-10 w-28 rounded-2xl animate-pulse" />
            <Skeleton className="h-10 w-28 rounded-2xl animate-pulse" />
          </div>
        ) : (
          <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-thin scrollbar-thumb-slate-200 dark:scrollbar-thumb-slate-800">
            {departments.map((dept) => {
              const isSelected = selectedDeptId === dept.id;
              return (
                <button
                  key={dept.id}
                  onClick={() => {
                    setSelectedDeptId(dept.id);
                    setCurrentPage(1);
                    setSearchQuery('');
                  }}
                  className={`flex-shrink-0 px-5 py-2.5 rounded-2xl text-xs font-semibold border transition-all duration-200 ${
                    isSelected
                      ? 'bg-primary text-primary-foreground border-primary shadow-sm'
                      : 'bg-card text-muted-foreground hover:text-foreground border-border hover:bg-slate-50 dark:hover:bg-slate-900/50'
                  }`}
                >
                  {dept.name} ({dept.code})
                </button>
              );
            })}
          </div>
        )}
      </div>

      {selectedDeptId === null ? (
        <Card className="flex flex-col items-center justify-center text-center p-16 gap-4 bg-card/60">
          <div className="p-4 bg-primary/10 rounded-full text-primary">
            <Building className="w-12 h-12" />
          </div>
          <div>
            <h3 className="font-bold text-sm">Chưa có phòng ban nào hoạt động</h3>
            <p className="text-xs text-muted-foreground mt-1 max-w-sm">
              Vui lòng tạo phòng ban nghiệp vụ trước khi quản lý nhân sự.
            </p>
          </div>
        </Card>
      ) : (
        <div className="flex flex-col gap-6">
          {/* Controls bar */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-4">
            <div className="relative w-full sm:max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
              <input
                type="text"
                placeholder="Tìm nhân sự..."
                value={searchQuery}
                onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
                className="h-10 w-full rounded-xl border border-input bg-card pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-ring"
              />
            </div>

            <Button onClick={handleOpenCreate} className="h-10 shrink-0">
              <Plus className="w-4 h-4 mr-2" />
              Thêm nhân sự
            </Button>
          </div>

          {/* Users Table */}
          <Card className="overflow-hidden">
            {loadingUsers ? (
              <div className="p-6 flex flex-col gap-4">
                <Skeleton className="h-10 w-full animate-pulse" />
                <Skeleton className="h-12 w-full animate-pulse" />
              </div>
            ) : paginatedUsers.length === 0 ? (
              <div className="p-16 flex flex-col items-center justify-center text-center gap-4">
                <div className="p-4 bg-slate-100 dark:bg-slate-900 rounded-full text-muted-foreground">
                  <Users className="w-12 h-12" />
                </div>
                <div>
                  <h4 className="font-bold text-sm">Không tìm thấy nhân sự nào</h4>
                  <p className="text-xs text-muted-foreground mt-1">Chưa có tài khoản nào thuộc phòng ban này.</p>
                </div>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="border-b border-border bg-slate-50 dark:bg-slate-900/40 text-muted-foreground font-semibold">
                      <th className="px-6 py-4">Nhân sự</th>
                      <th className="px-6 py-4">Họ và tên</th>
                      <th className="px-6 py-4">Số điện thoại</th>
                      <th className="px-6 py-4">Vai trò</th>
                      <th className="px-6 py-4">Ngày đăng ký</th>
                      <th className="px-6 py-4 text-right pr-12">Thao tác</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {paginatedUsers.map((user) => (
                      <tr key={user.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors">
                        <td className="px-6 py-4">
                          <div className="flex items-center gap-3">
                            <div className="w-8 h-8 rounded-full border border-border bg-primary/10 text-primary flex items-center justify-center font-bold text-xs">
                              {(user.fullName || user.username).charAt(0).toUpperCase()}
                            </div>
                            <div className="flex flex-col">
                              <span className="font-semibold text-slate-800 dark:text-slate-200">{user.username}</span>
                              <span className="text-[10px] text-muted-foreground font-mono">{user.email}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-6 py-4 font-semibold text-slate-700 dark:text-slate-300">
                          {user.fullName || 'N/A'}
                        </td>
                        <td className="px-6 py-4 font-mono font-medium text-slate-660 dark:text-slate-400">
                          {user.phone || 'N/A'}
                        </td>
                        <td className="px-6 py-4">
                          <span className="inline-flex px-2 py-0.5 rounded text-[10px] font-semibold bg-blue-50 text-blue-600 dark:bg-blue-950/20 dark:text-blue-400">
                            {user.role.replace('ROLE_', '')}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-muted-foreground">
                          {user.createdAt ? new Date(user.createdAt).toLocaleDateString('vi-VN') : 'N/A'}
                        </td>
                        <td className="px-6 py-4">
                          <div className="flex items-center justify-end gap-2">
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenDetail(user)}
                              className="h-8 rounded-xl gap-1.5 text-slate-700 dark:text-slate-300 hover:bg-slate-100"
                              title="Xem chi tiết"
                            >
                              <Eye className="w-3.5 h-3.5" />
                              <span>Chi tiết</span>
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenEdit(user)}
                              className="h-8 rounded-xl gap-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 dark:hover:bg-blue-950/15"
                              title="Sửa nhân sự"
                            >
                              <Edit2 className="w-3.5 h-3.5" />
                              <span>Sửa</span>
                            </Button>
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleOpenDelete(user)}
                              className="h-8 rounded-xl gap-1.5 text-rose-600 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/15"
                              title="Khóa tài khoản"
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

          {/* Pagination Controls */}
          {filteredUsers.length > 0 && (
            <div className="flex flex-col sm:flex-row items-center justify-between gap-4 mt-2 text-xs text-muted-foreground font-medium">
              <div className="flex items-center gap-4">
                <span>
                  Hiển thị {Math.min(filteredUsers.length, (currentPage - 1) * itemsPerPage + 1)} -{' '}
                  {Math.min(filteredUsers.length, currentPage * itemsPerPage)} trong tổng số{' '}
                  {filteredUsers.length} nhân sự
                </span>
                <div className="flex items-center gap-2">
                  <span>Số bản ghi mỗi trang:</span>
                  <select
                    value={itemsPerPage}
                    onChange={(e) => {
                      setItemsPerPage(Number(e.target.value));
                      setCurrentPage(1);
                    }}
                    className="bg-card border border-border rounded-lg p-1 text-xs focus:outline-none"
                  >
                    <option value={5}>5</option>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                  </select>
                </div>
              </div>

              <div className="flex justify-end items-center gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                  disabled={currentPage === 1}
                >
                  Trước
                </Button>
                <span className="font-semibold text-foreground">
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
            </div>
          )}
        </div>
      )}

      {/* Create User Modal */}
      <Modal
        isOpen={modalMode === 'CREATE'}
        onClose={() => setModalMode(null)}
        title="Đăng ký tài khoản nhân sự mới"
      >
        <form onSubmit={createForm.handleSubmit(onCreateSubmit)} className="flex flex-col gap-4">
          <Input
            label="Họ và tên nhân sự"
            placeholder="Nhập họ và tên đầy đủ..."
            error={createForm.formState.errors.fullName?.message}
            {...createForm.register('fullName')}
          />
          <Input
            label="Số điện thoại di động"
            placeholder="Ví dụ: 0987654321..."
            error={createForm.formState.errors.phone?.message}
            {...createForm.register('phone')}
          />
          <Input
            label="Tên đăng nhập"
            placeholder="Ví dụ: employee_vcc..."
            error={createForm.formState.errors.username?.message}
            {...createForm.register('username')}
          />
          <Input
            label="Địa chỉ email"
            type="email"
            placeholder="Nhập email doanh nghiệp..."
            error={createForm.formState.errors.email?.message}
            {...createForm.register('email')}
          />
          
          <Input
            label="Mật khẩu tài khoản"
            type="password"
            placeholder="Nhập mật khẩu truy cập..."
            error={createForm.formState.errors.password?.message}
            {...createForm.register('password')}
          />
          <Input
            label="Xác nhận mật khẩu"
            type="password"
            placeholder="Nhập lại mật khẩu..."
            error={createForm.formState.errors.confirmPassword?.message}
            {...createForm.register('confirmPassword')}
          />

          <Select
            label="Vai trò hệ thống"
            options={roleOptions}
            error={createForm.formState.errors.role?.message}
            {...createForm.register('role')}
          />

          <div className="flex justify-end gap-3 mt-2">
            <Button type="button" variant="outline" onClick={() => setModalMode(null)}>
              Hủy
            </Button>
            <Button type="submit" loading={isCreating}>
              Lưu lại
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit User Modal */}
      <Modal
        isOpen={modalMode === 'EDIT'}
        onClose={() => setModalMode(null)}
        title="Cập nhật tài khoản nhân sự"
      >
        <form onSubmit={editForm.handleSubmit(onEditSubmit)} className="flex flex-col gap-4">
          <Input
            label="Họ và tên nhân sự"
            placeholder="Nhập họ và tên đầy đủ..."
            error={editForm.formState.errors.fullName?.message}
            {...editForm.register('fullName')}
          />
          <Input
            label="Số điện thoại di động"
            placeholder="Ví dụ: 0987654321..."
            error={editForm.formState.errors.phone?.message}
            {...editForm.register('phone')}
          />
          <Input
            label="Tên đăng nhập"
            placeholder="Ví dụ: employee_vcc..."
            error={editForm.formState.errors.username?.message}
            {...editForm.register('username')}
          />
          <Input
            label="Địa chỉ email"
            type="email"
            placeholder="Nhập email doanh nghiệp..."
            error={editForm.formState.errors.email?.message}
            {...editForm.register('email')}
          />

          {/* Role is read-only — immutable after account creation */}
          <div className="flex flex-col gap-1">
            <label className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider">Vai trò hệ thống</label>
            <div className="flex items-center gap-2 h-10 px-3 rounded-xl border border-input bg-muted/40 text-xs font-semibold text-muted-foreground cursor-not-allowed">
              <ShieldCheck className="w-3.5 h-3.5 text-blue-500 flex-shrink-0" />
              <span>{selectedUser?.role?.replace('ROLE_', '') ?? '—'}</span>
              <span className="ml-auto text-[10px] font-normal italic text-muted-foreground/60">Không thể thay đổi</span>
            </div>
          </div>

          <div className="flex justify-end gap-3 mt-2">
            <Button type="button" variant="outline" onClick={() => setModalMode(null)}>
              Hủy
            </Button>
            <Button type="submit" loading={isEditing}>
              Lưu lại
            </Button>
          </div>
        </form>
      </Modal>

      {/* Detail Modal */}
      <Modal
        isOpen={modalMode === 'DETAIL'}
        onClose={() => setModalMode(null)}
        title={`Hồ sơ nhân viên: ${selectedUser?.username}`}
      >
        <div className="flex flex-col gap-4 text-xs">
          <div className="flex items-center gap-4 bg-slate-50 dark:bg-slate-900 p-4 rounded-xl border border-border">
            <div className="w-12 h-12 rounded-full border border-border bg-primary/10 text-primary flex items-center justify-center font-bold text-lg">
              {(selectedUser?.fullName || selectedUser?.username || 'U').charAt(0).toUpperCase()}
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-sm font-bold text-slate-800 dark:text-slate-200">{selectedUser?.fullName || selectedUser?.username}</span>
              <span className="text-[11px] text-slate-500 font-semibold">Tên đăng nhập: <strong className="text-primary font-mono">{selectedUser?.username}</strong></span>
              <span className="text-[10px] text-muted-foreground font-mono">{selectedUser?.email}</span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Số điện thoại:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1 font-mono">
                <Phone className="w-3.5 h-3.5 text-primary flex-shrink-0" />
                {selectedUser?.phone || 'N/A'}
              </span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Vai trò hệ thống:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5 text-blue-500 flex-shrink-0" />
                {selectedUser?.role.replace('ROLE_', '')}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Phòng ban trực thuộc:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1">
                <Building2 className="w-3.5 h-3.5 text-primary flex-shrink-0" />
                {getDeptName(selectedUser?.departmentId || null)}
              </span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Thời gian đăng ký:</span>
              <span className="font-semibold">
                {selectedUser?.createdAt ? new Date(selectedUser.createdAt).toLocaleString('vi-VN') : 'N/A'}
              </span>
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
        title="Xác nhận xóa tài khoản"
        message="Bạn có chắc chắn muốn xoá mục này? Hành động này không thể hoàn tác."
        confirmLabel="Xoá"
        isLoading={isDeleting}
        variant="danger"
      />
    </div>
  );
};
