import React, { useState, useEffect } from 'react';
import { useDocuments } from '@/hooks/useDocuments';
import { useDepartments } from '@/hooks/useDepartments';
import { useAuth } from '@/store/AuthContext';
import { useToast } from '@/components/Toast';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { Modal } from '@/components/Modal';
import { Input } from '@/components/Input';
import { Select } from '@/components/Select';
import { Skeleton } from '@/components/Skeleton';
import {
  FileText,
  Plus,
  Search,
  Download,
  Trash2,
  Edit2,
  Eye,
  Info,
  Building,
  ShieldAlert,
  Share2
} from 'lucide-react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { Document } from '@/types';
import { documentService } from '@/services/api';
import { ConfirmDialog } from '@/components/ConfirmDialog';

const uploadSchema = z.object({
  title: z.string().min(2, 'Tiêu đề tài liệu không được để trống'),
  file: z.any().refine((files) => files && files.length > 0, 'Vui lòng chọn tệp tin cần tải lên'),
});

const editSchema = z.object({
  title: z.string().min(2, 'Tiêu đề tài liệu không được để trống'),
});

type UploadSchema = z.infer<typeof uploadSchema>;
type EditSchema = z.infer<typeof editSchema>;

export const DocumentsPage: React.FC = () => {
  const { user } = useAuth();
  const { showToast } = useToast();
  const { departments } = useDepartments();

  // Pagination & Filtering
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(5);
  const [searchQuery, setSearchQuery] = useState('');

  // Documents State
  const {
    documents,
    totalElements,
    isLoading,
    uploadDocument,
    isUploading,
    deleteDocument,
    isDeleting,
    editDocument,
    isEditing,
  } = useDocuments(currentPage - 1, itemsPerPage);

  // Modals state
  const [modalMode, setModalMode] = useState<'CREATE' | 'EDIT' | 'DETAIL' | 'DELETE_CONFIRM' | 'SHARE' | null>(null);
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);

  // Sharing states
  const [activeAliases, setActiveAliases] = useState<Document[]>([]);
  const [loadingAliases, setLoadingAliases] = useState(false);
  const [shareDeptId, setShareDeptId] = useState<string>('');

  // Forms
  const {
    register: registerUpload,
    handleSubmit: handleUploadSubmit,
    reset: resetUpload,
    formState: { errors: uploadErrors },
  } = useForm<UploadSchema>({
    resolver: zodResolver(uploadSchema),
  });

  const {
    register: registerEdit,
    handleSubmit: handleEditSubmit,
    reset: resetEdit,
    setValue: setEditValue,
    formState: { errors: editErrors },
  } = useForm<EditSchema>({
    resolver: zodResolver(editSchema),
  });

  // Fetch active shares/aliases when Share Modal opens
  const fetchAliases = async (docId: string) => {
    setLoadingAliases(true);
    try {
      const res = await documentService.listDocumentAliases(docId);
      setActiveAliases(res.data || []);
    } catch (err: any) {
      showToast('Không thể tải danh sách liên kết chia sẻ.', 'error');
    } finally {
      setLoadingAliases(false);
    }
  };

  useEffect(() => {
    if (modalMode === 'SHARE' && selectedDoc) {
      fetchAliases(selectedDoc.id);
      setShareDeptId('');
    }
  }, [modalMode, selectedDoc]);

  const onUploadSubmit = async (values: UploadSchema) => {
    try {
      const file = values.file[0];
      await uploadDocument({ title: values.title, file });
      showToast('Tải lên tài liệu gốc thành công!', 'success');
      setModalMode(null);
      resetUpload();
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Có lỗi xảy ra khi tải lên tài liệu.', 'error');
    }
  };

  const onEditSubmit = async (values: EditSchema) => {
    if (selectedDoc) {
      try {
        await editDocument(selectedDoc.id, values.title);
        showToast('Cập nhật tài liệu thành công!', 'success');
        setModalMode(null);
      } catch (err: any) {
        showToast(err.response?.data?.message || 'Không thể chỉnh sửa tài liệu.', 'error');
      }
    }
  };

  const executeDelete = async () => {
    if (selectedDoc) {
      try {
        await deleteDocument(selectedDoc.id);
        showToast('Đã xóa mềm tài liệu gốc và vô hiệu hóa liên kết Alias.', 'success');
      } catch (err: any) {
        showToast(err.response?.data?.message || 'Không có quyền xóa tài liệu.', 'error');
      } finally {
        setModalMode(null);
      }
    }
  };

  const handleDownload = async (aliasId: string, filename: string) => {
    try {
      // In private department folders, users download original docs via resolveAlias as well because resolution is uniform
      const blob = await documentService.resolveAlias(aliasId);
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      showToast('Tải xuống tài liệu thành công!', 'success');
    } catch (err: any) {
      showToast('Bạn không có quyền tải xuống tài liệu này.', 'error');
    }
  };

  const handleOpenEdit = (doc: Document) => {
    setSelectedDoc(doc);
    setEditValue('title', doc.title);
    setModalMode('EDIT');
  };

  const handleOpenDetail = (doc: Document) => {
    setSelectedDoc(doc);
    setModalMode('DETAIL');
  };

  const handleOpenDelete = (doc: Document) => {
    setSelectedDoc(doc);
    setModalMode('DELETE_CONFIRM');
  };

  const handleOpenShare = (doc: Document) => {
    setSelectedDoc(doc);
    setModalMode('SHARE');
  };

  // Confirmation states
  const [showShareConfirm, setShowShareConfirm] = useState(false);
  const [showRevokeConfirm, setShowRevokeConfirm] = useState(false);
  const [aliasToRevoke, setAliasToRevoke] = useState<string | null>(null);
  const [isSharing, setIsSharing] = useState(false);
  const [isRevoking, setIsRevoking] = useState(false);

  const handleCreateShare = () => {
    if (!selectedDoc || !shareDeptId) return;
    setShowShareConfirm(true);
  };

  const executeCreateShare = async () => {
    if (!selectedDoc || !shareDeptId) return;
    setIsSharing(true);
    try {
      await documentService.createAlias({
        originalDocumentId: selectedDoc.id,
        aliasDepartmentId: shareDeptId,
      });
      showToast('Chia sẻ liên kết tài liệu thành công!', 'success');
      setShareDeptId('');
      fetchAliases(selectedDoc.id);
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Không thể chia sẻ tài liệu.', 'error');
    } finally {
      setIsSharing(false);
      setShowShareConfirm(false);
    }
  };

  const handleRevokeShare = (aliasId: string) => {
    setAliasToRevoke(aliasId);
    setShowRevokeConfirm(true);
  };

  const executeRevokeShare = async () => {
    if (!aliasToRevoke) return;
    setIsRevoking(true);
    try {
      await documentService.deleteAlias(aliasToRevoke);
      showToast('Đã thu hồi chia sẻ liên kết thành công!', 'success');
      if (selectedDoc) {
        fetchAliases(selectedDoc.id);
      }
    } catch (err: any) {
      showToast(err.response?.data?.message || 'Không thể thu hồi chia sẻ.', 'error');
    } finally {
      setIsRevoking(false);
      setAliasToRevoke(null);
      setShowRevokeConfirm(false);
    }
  };

  // Filter local search results
  const filteredDocs = documents.filter((doc) => {
    return doc.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
           doc.businessCode.toLowerCase().includes(searchQuery.toLowerCase());
  });

  const totalPages = Math.ceil(totalElements / itemsPerPage) || 1;

  const getDeptName = (deptId: string | null) => {
    if (!deptId) return 'N/A';
    const dept = departments.find((d) => d.id === deptId);
    return dept ? `${dept.name} (${dept.code})` : deptId;
  };

  // UI Rules: All business roles can upload original documents.
  const canUpload = user?.role === 'ROLE_BOARD' || user?.role === 'ROLE_DEPT_MANAGER' || user?.role === 'ROLE_EMPLOYEE';
  // Share (Create/Delete Alias): Only Department Admin (ROLE_DEPT_MANAGER) and Employee (ROLE_EMPLOYEE)
  const canShare = user?.role === 'ROLE_DEPT_MANAGER' || user?.role === 'ROLE_EMPLOYEE';
  // CRUD (Edit/Delete original doc): Only Department Admin (ROLE_DEPT_MANAGER) or SYSTEM_ADMIN
  const canEditOrDeleteOriginal = user?.role === 'ROLE_DEPT_MANAGER' || user?.role === 'SYSTEM_ADMIN';

  // Filter target eligible departments to share (exclude own department and BOARD)
  const eligibleDepts = departments.filter(
    (d) => d.id !== user?.departmentId && d.code.toUpperCase() !== 'BOARD'
  );

  const shareDeptOptions = [
    { value: '', label: '-- Chọn phòng ban nghiệp vụ --' },
    ...eligibleDepts.map((d) => ({
      value: d.id,
      label: `${d.name} (${d.code})`,
    })),
  ];

  return (
    <div className="flex flex-col gap-6">
      {/* Controls Header */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        <div className="relative w-full sm:max-w-xs">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Tìm kiếm tài liệu..."
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
            className="h-10 w-full rounded-xl border border-input bg-card pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>

        {canUpload && (
          <Button onClick={() => setModalMode('CREATE')} className="h-10 shrink-0">
            <Plus className="w-4 h-4 mr-2" />
            Tải lên tài liệu
          </Button>
        )}
      </div>

      {/* Table view */}
      <Card className="overflow-hidden">
        {isLoading ? (
          <div className="p-6 flex flex-col gap-4">
            <Skeleton className="h-10 w-full animate-pulse" />
            <Skeleton className="h-20 w-full animate-pulse" />
          </div>
        ) : filteredDocs.length === 0 ? (
          <div className="p-16 flex flex-col items-center justify-center text-center gap-4">
            <div className="p-4 bg-slate-100 dark:bg-slate-900 rounded-full text-muted-foreground">
              <FileText className="w-12 h-12" />
            </div>
            <div>
              <h4 className="font-bold text-sm">Không tìm thấy tài liệu nào</h4>
              <p className="text-xs text-muted-foreground mt-1">Chưa có hồ sơ tài liệu nào thuộc phòng ban của bạn.</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-border bg-slate-50 dark:bg-slate-900/40 text-muted-foreground font-semibold">
                  <th className="px-6 py-4">Mã tài liệu</th>
                  <th className="px-6 py-4">Tiêu đề tài liệu</th>
                  <th className="px-6 py-4">Phòng ban sở hữu</th>
                  <th className="px-6 py-4">Ngày đăng tải</th>
                  <th className="px-6 py-4 text-right pr-12">Thao tác</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filteredDocs.map((doc) => (
                  <tr key={doc.id} className="hover:bg-slate-50/50 dark:hover:bg-slate-900/10 transition-colors">
                    <td className="px-6 py-4 font-mono font-bold text-primary">{doc.businessCode}</td>
                    <td className="px-6 py-4 font-semibold text-slate-800 dark:text-slate-200 truncate max-w-xs">
                      {doc.title}
                    </td>
                    <td className="px-6 py-4 font-medium text-slate-500">{getDeptName(doc.ownerDepartmentId)}</td>
                    <td className="px-6 py-4 text-muted-foreground">
                      {doc.createdAt ? new Date(doc.createdAt).toLocaleDateString('vi-VN') : 'N/A'}
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center justify-end gap-2">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleOpenDetail(doc)}
                          className="h-8 rounded-xl gap-1.5 text-slate-700 dark:text-slate-300 hover:bg-slate-100"
                          title="Xem chi tiết"
                        >
                          <Eye className="w-3.5 h-3.5" />
                          <span>Chi tiết</span>
                        </Button>
                        
                        {canEditOrDeleteOriginal && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenEdit(doc)}
                            className="h-8 rounded-xl gap-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 dark:hover:bg-blue-950/15"
                            title="Sửa tài liệu"
                          >
                            <Edit2 className="w-3.5 h-3.5" />
                            <span>Sửa</span>
                          </Button>
                        )}

                        {canShare && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenShare(doc)}
                            className="h-8 rounded-xl gap-1.5 text-indigo-600 hover:text-indigo-700 hover:bg-indigo-50 dark:hover:bg-indigo-950/15"
                            title="Chia sẻ tài liệu"
                          >
                            <Share2 className="w-3.5 h-3.5" />
                            <span>Chia sẻ</span>
                          </Button>
                        )}

                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => handleDownload(doc.id, doc.title)}
                          className="h-8 rounded-xl gap-1.5 text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50 dark:hover:bg-emerald-950/15"
                          title="Tải xuống tệp tin"
                        >
                          <Download className="w-3.5 h-3.5" />
                          <span>Tải về</span>
                        </Button>

                        {canEditOrDeleteOriginal && (
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleOpenDelete(doc)}
                            className="h-8 rounded-xl gap-1.5 text-rose-600 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/15"
                            title="Xóa mềm"
                          >
                            <Trash2 className="w-3.5 h-3.5" />
                            <span>Xóa</span>
                          </Button>
                        )}
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
      {totalElements > 0 && (
        <div className="flex flex-col sm:flex-row items-center justify-between gap-4 mt-2 text-xs text-muted-foreground font-medium">
          <div className="flex items-center gap-4">
            <span>
              Hiển thị {Math.min(totalElements, (currentPage - 1) * itemsPerPage + 1)} -{' '}
              {Math.min(totalElements, currentPage * itemsPerPage)} trong tổng số{' '}
              {totalElements} tài liệu
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

      {/* Upload Modal */}
      <Modal isOpen={modalMode === 'CREATE'} onClose={() => setModalMode(null)} title="Tải lên tài liệu gốc mới">
        <form onSubmit={handleUploadSubmit(onUploadSubmit)} className="flex flex-col gap-4">
          <Input
            label="Tiêu đề tài liệu"
            placeholder="Nhập tiêu đề hồ sơ..."
            error={uploadErrors.title?.message}
            {...registerUpload('title')}
          />
          
          <div className="flex flex-col gap-1.5">
            <label className="text-[10px] font-bold text-muted-foreground uppercase">Tệp tin tài liệu</label>
            <input
              type="file"
              accept=".pdf,.docx,.xlsx,.pptx"
              className="file:mr-4 file:py-2 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-primary/10 file:text-primary hover:file:bg-primary/20 text-xs text-slate-500 border border-input rounded-xl p-3 bg-background"
              {...registerUpload('file')}
            />
            {uploadErrors.file?.message && (
              <span className="text-xs font-semibold text-destructive mt-1">
                {uploadErrors.file?.message as string}
              </span>
            )}
            <div className="flex items-start gap-2 mt-1.5 p-3 bg-blue-500/5 border border-blue-500/10 rounded-xl text-[10px] text-slate-500 leading-relaxed">
              <Info className="w-4 h-4 text-blue-500 flex-shrink-0" />
              <span>Định dạng hỗ trợ: <strong>.pdf, .docx, .xlsx, .pptx</strong>. Dung lượng tối đa <strong>50MB</strong>.</span>
            </div>
          </div>

          <div className="flex justify-end gap-3 mt-2">
            <Button type="button" variant="outline" onClick={() => setModalMode(null)}>
              Hủy
            </Button>
            <Button type="submit" loading={isUploading}>
              Bắt đầu tải lên
            </Button>
          </div>
        </form>
      </Modal>

      {/* Edit Modal */}
      <Modal isOpen={modalMode === 'EDIT'} onClose={() => setModalMode(null)} title="Cập nhật thông tin tài liệu">
        <form onSubmit={handleEditSubmit(onEditSubmit)} className="flex flex-col gap-4">
          <Input
            label="Tiêu đề tài liệu"
            placeholder="Nhập tiêu đề mới..."
            error={editErrors.title?.message}
            {...registerEdit('title')}
          />

          <div className="flex justify-end gap-3 mt-2">
            <Button type="button" variant="outline" onClick={() => setModalMode(null)}>
              Hủy
            </Button>
            <Button type="submit" loading={isEditing}>
              Lưu thay đổi
            </Button>
          </div>
        </form>
      </Modal>

      {/* Detail Modal */}
      <Modal
        isOpen={modalMode === 'DETAIL'}
        onClose={() => setModalMode(null)}
        title={`Thông tin tài liệu: ${selectedDoc?.title}`}
      >
        <div className="flex flex-col gap-4 text-xs">
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Mã tài liệu:</span>
              <span className="font-mono font-bold text-primary text-sm">{selectedDoc?.businessCode}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Kích thước file:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200">
                {selectedDoc?.fileSize ? `${(selectedDoc.fileSize / 1024 / 1024).toFixed(2)} MB` : 'N/A'}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Phòng ban sở hữu:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1">
                <Building className="w-3.5 h-3.5 text-primary flex-shrink-0" />
                {getDeptName(selectedDoc?.ownerDepartmentId || null)}
              </span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Mã băm SHA-256:</span>
              <span className="font-mono font-semibold text-slate-600 dark:text-slate-400 break-all select-all">
                {selectedDoc?.hash || 'N/A'}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 pt-2 border-t border-border">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Thời gian đăng tải:</span>
              <span className="font-semibold">
                {selectedDoc?.createdAt ? new Date(selectedDoc.createdAt).toLocaleString('vi-VN') : 'N/A'}
              </span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Cập nhật cuối:</span>
              <span className="font-semibold">
                {selectedDoc?.updatedAt ? new Date(selectedDoc.updatedAt).toLocaleString('vi-VN') : 'N/A'}
              </span>
            </div>
          </div>

          <div className="flex justify-end mt-4">
            <Button onClick={() => setModalMode(null)}>Đóng</Button>
          </div>
        </div>
      </Modal>

      {/* Sharing / Alias Management Modal */}
      <Modal
        isOpen={modalMode === 'SHARE'}
        onClose={() => setModalMode(null)}
        title={`Chia sẻ liên kết tài liệu: ${selectedDoc?.title}`}
      >
        <div className="flex flex-col gap-4 text-xs">
          <div className="p-3 bg-indigo-500/5 border border-indigo-500/10 text-slate-600 dark:text-slate-400 rounded-xl leading-relaxed">
            Hệ thống tối ưu hóa tài liệu bằng cách tạo <strong>Liên kết Alias</strong>. Phòng ban nhận có thể xem và tải xuống tài liệu mà không tốn dung lượng sao chép tệp.
          </div>

          {/* Active shares list */}
          <div className="flex flex-col gap-2">
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Các phòng ban đang được chia sẻ</span>
            {loadingAliases ? (
              <div className="flex flex-col gap-1.5">
                <Skeleton className="h-8 w-full animate-pulse" />
                <Skeleton className="h-8 w-full animate-pulse" />
              </div>
            ) : activeAliases.length === 0 ? (
              <p className="text-xs text-muted-foreground italic py-2">Tài liệu này chưa được chia sẻ với phòng ban nào.</p>
            ) : (
              <div className="flex flex-col gap-1.5 max-h-40 overflow-y-auto pr-1">
                {activeAliases.map((alias) => (
                  <div key={alias.id} className="flex items-center justify-between p-2 bg-slate-50 dark:bg-slate-900 border border-border rounded-xl">
                    <span className="font-semibold">{getDeptName(alias.ownerDepartmentId)}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRevokeShare(alias.id)}
                      className="h-7 px-2 text-rose-500 hover:text-rose-600 hover:bg-rose-500/10 rounded-lg gap-1.5"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                      <span>Thu hồi</span>
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Add new share target */}
          <div className="flex flex-col gap-2 pt-3 border-t border-border">
            <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">Thiết lập chia sẻ mới</span>
            <div className="flex items-end gap-3">
              <div className="flex-grow">
                <Select
                  options={shareDeptOptions}
                  value={shareDeptId}
                  onChange={(e) => setShareDeptId(e.target.value)}
                />
              </div>
              <Button onClick={handleCreateShare} disabled={!shareDeptId} className="h-10">
                <Share2 className="w-4 h-4 mr-2" />
                Chia sẻ
              </Button>
            </div>
          </div>

          <div className="flex justify-end mt-4">
            <Button variant="outline" onClick={() => setModalMode(null)}>Đóng</Button>
          </div>
        </div>
      </Modal>

      {/* Delete Confirmation Dialog */}
      <ConfirmDialog
        isOpen={modalMode === 'DELETE_CONFIRM'}
        onClose={() => setModalMode(null)}
        onConfirm={executeDelete}
        title="Xác nhận xóa tài liệu"
        message="Bạn có chắc chắn muốn xoá mục này? Hành động này không thể hoàn tác."
        confirmLabel="Xoá"
        isLoading={isDeleting}
        variant="danger"
      />

      {/* Share Confirmation Dialog */}
      <ConfirmDialog
        isOpen={showShareConfirm}
        onClose={() => setShowShareConfirm(false)}
        onConfirm={executeCreateShare}
        title="Xác nhận chia sẻ tài liệu"
        message="Bạn có chắc chắn muốn chia sẻ tài liệu này?"
        confirmLabel="Chia sẻ"
        isLoading={isSharing}
        variant="primary"
      />

      {/* Revoke Confirmation Dialog */}
      <ConfirmDialog
        isOpen={showRevokeConfirm}
        onClose={() => setShowRevokeConfirm(false)}
        onConfirm={executeRevokeShare}
        title="Xác nhận thu hồi chia sẻ"
        message="Bạn có chắc chắn muốn thu hồi quyền chia sẻ của tài liệu này?"
        confirmLabel="Thu hồi"
        isLoading={isRevoking}
        variant="warning"
      />
    </div>
  );
};
