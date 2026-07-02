import React, { useState, useEffect } from 'react';
import { useDepartments } from '@/hooks/useDepartments';
import { useAuth } from '@/store/AuthContext';
import { useToast } from '@/components/Toast';
import { Card } from '@/components/Card';
import { Button } from '@/components/Button';
import { Modal } from '@/components/Modal';
import { Skeleton } from '@/components/Skeleton';
import {
  FileText,
  Search,
  Download,
  Eye,
  Building
} from 'lucide-react';
import { Document } from '@/types';
import { documentService } from '@/services/api';

export const SharedDocumentsPage: React.FC = () => {
  const { user } = useAuth();
  const { showToast } = useToast();
  const { departments } = useDepartments();

  // Pagination & Filtering
  const [currentPage, setCurrentPage] = useState(1);
  const [itemsPerPage, setItemsPerPage] = useState(5);
  const [searchQuery, setSearchQuery] = useState('');

  // Local state for fetching shared documents
  const [sharedDocs, setSharedDocs] = useState<Document[]>([]);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(false);

  // Modals state
  const [selectedDoc, setSelectedDoc] = useState<Document | null>(null);
  const [isDetailOpen, setIsDetailOpen] = useState(false);

  const fetchSharedDocs = async () => {
    setIsLoading(true);
    try {
      const res = await documentService.listSharedDocuments(currentPage - 1, itemsPerPage);
      setSharedDocs(res.data?.content || []);
      setTotalElements(res.data?.totalElements || 0);
    } catch (err: any) {
      showToast('Không thể tải danh sách tài liệu chia sẻ.', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchSharedDocs();
  }, [currentPage, itemsPerPage]);

  const handleDownload = async (aliasId: string, filename: string) => {
    try {
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

  const handleOpenDetail = (doc: Document) => {
    setSelectedDoc(doc);
    setIsDetailOpen(true);
  };

  // Filter local search results
  const filteredDocs = sharedDocs.filter((doc) => {
    return doc.title.toLowerCase().includes(searchQuery.toLowerCase()) ||
           doc.businessCode.toLowerCase().includes(searchQuery.toLowerCase());
  });

  const totalPages = Math.ceil(totalElements / itemsPerPage) || 1;

  const getDeptName = (deptId: string | null) => {
    if (!deptId) return 'N/A';
    const dept = departments.find((d) => d.id === deptId);
    return dept ? `${dept.name} (${dept.code})` : deptId;
  };

  return (
    <div className="flex flex-col gap-6">
      {/* Controls Header */}
      <div className="flex flex-col md:flex-row items-stretch md:items-center justify-between gap-4">
        <div className="relative w-full sm:max-w-xs">
          <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
          <input
            type="text"
            placeholder="Tìm kiếm tài liệu chia sẻ..."
            value={searchQuery}
            onChange={(e) => { setSearchQuery(e.target.value); setCurrentPage(1); }}
            className="h-10 w-full rounded-xl border border-input bg-card pl-9 pr-4 text-xs focus:outline-none focus:ring-2 focus:ring-ring"
          />
        </div>
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
              <h4 className="font-bold text-sm">Không tìm thấy tài liệu chia sẻ nào</h4>
              <p className="text-xs text-muted-foreground mt-1">Các phòng ban khác chưa chia sẻ tài liệu nào cho phòng ban của bạn.</p>
            </div>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="border-b border-border bg-slate-50 dark:bg-slate-900/40 text-muted-foreground font-semibold">
                  <th className="px-6 py-4">Mã tài liệu</th>
                  <th className="px-6 py-4">Tiêu đề tài liệu</th>
                  <th className="px-6 py-4">Phòng ban chia sẻ</th>
                  <th className="px-6 py-4">Ngày nhận chia sẻ</th>
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
                    <td className="px-6 py-4 font-medium text-slate-500">{getDeptName(doc.creatorDepartmentId)}</td>
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

      {/* Detail Modal */}
      <Modal
        isOpen={isDetailOpen}
        onClose={() => setIsDetailOpen(false)}
        title={`Thông tin tài liệu chia sẻ: ${selectedDoc?.title}`}
      >
        <div className="flex flex-col gap-4 text-xs">
          <div className="grid grid-cols-2 gap-4">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Mã liên kết (Alias):</span>
              <span className="font-mono font-bold text-primary text-sm">{selectedDoc?.businessCode}</span>
            </div>
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Phòng ban chia sẻ:</span>
              <span className="font-semibold text-slate-800 dark:text-slate-200 flex items-center gap-1">
                <Building className="w-3.5 h-3.5 text-primary flex-shrink-0" />
                {getDeptName(selectedDoc?.creatorDepartmentId || null)}
              </span>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 pt-2 border-t border-border">
            <div className="flex flex-col gap-0.5">
              <span className="text-muted-foreground font-medium">Thời gian nhận chia sẻ:</span>
              <span className="font-semibold">
                {selectedDoc?.createdAt ? new Date(selectedDoc.createdAt).toLocaleString('vi-VN') : 'N/A'}
              </span>
            </div>
          </div>

          <div className="flex justify-end mt-4">
            <Button onClick={() => setIsDetailOpen(false)}>Đóng</Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
