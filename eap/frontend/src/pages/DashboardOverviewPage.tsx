import React from 'react';
import { useAuth } from '@/store/AuthContext';
import { Card } from '@/components/Card';
import { Building2, Users, FileText, Share2, ShieldCheck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { departmentService, userService, documentService } from '@/services/api';
import { Skeleton } from '@/components/Skeleton';
import { Department, User } from '@/types';

export const DashboardOverviewPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'SYSTEM_ADMIN';

  // React Queries to populate real dashboard metrics
  const { data: deptsData, isLoading: loadingDepts } = useQuery({
    queryKey: ['departments'],
    queryFn: departmentService.listDepartments,
  });

  const { data: usersData, isLoading: loadingUsers } = useQuery({
    queryKey: ['users'],
    queryFn: isAdmin
      ? userService.listUsers
      : async () => ({
          success: true,
          code: 'SUCCESS',
          timestamp: new Date().toISOString(),
          data: [] as User[],
        }),
    enabled: isAdmin,
  });

  const { data: docsData, isLoading: loadingDocs } = useQuery({
    queryKey: ['original-documents'],
    queryFn: () => documentService.listOriginalDocuments(0, 10),
    enabled: !isAdmin,
  });

  const totalDepts = deptsData?.data?.length || 0;
  const totalUsers = usersData?.data?.length || 0;
  const totalDocs = docsData?.data?.totalElements || 0;

  const departments = deptsData?.data || [];
  const userDept = departments.find((d) => d.id === user?.departmentId);
  const userDeptName = user?.role === 'SYSTEM_ADMIN' ? 'Quản trị hệ thống' : (userDept ? userDept.name : 'N/A');

  return (
    <div className="flex flex-col gap-8">
      {/* Greetings Card */}
      <Card className="p-8 bg-gradient-to-r from-primary/10 via-transparent to-transparent flex flex-col gap-2 relative overflow-hidden">
        <div className="absolute right-0 top-0 translate-x-12 -translate-y-12 w-64 h-64 bg-primary/5 rounded-full blur-3xl pointer-events-none" />
        <h1 className="text-2xl font-bold tracking-tight text-slate-900 dark:text-white">
          Xin chào {user?.fullName || user?.username}!
        </h1>
        <p className="text-sm text-muted-foreground max-w-xl font-medium">
          Bạn thuộc phòng ban {userDeptName}.
        </p>
      </Card>

      {/* Stats Section */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {isAdmin ? (
          <>
            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-blue-500/10 text-blue-500 rounded-2xl">
                <Building2 className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Tổng phòng ban</span>
                {loadingDepts ? (
                  <Skeleton className="h-8 w-16 mt-1" />
                ) : (
                  <h3 className="text-2xl font-bold mt-0.5">{totalDepts}</h3>
                )}
              </div>
            </Card>

            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-emerald-500/10 text-emerald-500 rounded-2xl">
                <Users className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Tổng tài khoản</span>
                {loadingUsers ? (
                  <Skeleton className="h-8 w-16 mt-1" />
                ) : (
                  <h3 className="text-2xl font-bold mt-0.5">{totalUsers}</h3>
                )}
              </div>
            </Card>

            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-purple-500/10 text-purple-500 rounded-2xl">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Quyền kiểm trị</span>
                <h3 className="text-sm font-semibold mt-1 text-purple-600 dark:text-purple-400">System Admin</h3>
              </div>
            </Card>
          </>
        ) : (
          <>
            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-blue-500/10 text-blue-500 rounded-2xl">
                <FileText className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Tài liệu nội bộ</span>
                {loadingDocs ? (
                  <Skeleton className="h-8 w-16 mt-1" />
                ) : (
                  <h3 className="text-2xl font-bold mt-0.5">{totalDocs}</h3>
                )}
              </div>
            </Card>

            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-emerald-500/10 text-emerald-500 rounded-2xl">
                <Share2 className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Liên kết chia sẻ</span>
                <h3 className="text-xs font-medium mt-1.5 text-muted-foreground">Phòng ban</h3>
              </div>
            </Card>

            <Card className="p-6 flex items-center gap-5">
              <div className="p-4 bg-orange-500/10 text-orange-500 rounded-2xl">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-semibold text-muted-foreground uppercase tracking-wider">Vai trò nghiệp vụ</span>
                <h3 className="text-sm font-semibold mt-1 text-orange-600 dark:text-orange-400">
                  {user?.role.replace('ROLE_', '')}
                </h3>
              </div>
            </Card>
          </>
        )}
      </div>

      {/* Info Guidelines Card */}
      <Card className="p-8 flex flex-col gap-4">
        <h3 className="text-base font-bold">Hướng dẫn sử dụng nhanh</h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-sm">
          <div className="flex flex-col gap-1.5">
            <span className="font-semibold text-slate-800 dark:text-slate-200">Cô lập dữ liệu (Data Isolation)</span>
            <p className="text-muted-foreground text-xs leading-relaxed">
              Hệ thống tự động cô lập tài liệu gốc theo từng phòng ban. Nhân viên chỉ xem được các tài liệu thuộc phòng ban của mình, ngoại trừ các tài liệu được chia sẻ thông qua các liên kết Alias.
            </p>
          </div>
          <div className="flex flex-col gap-1.5">
            <span className="font-semibold text-slate-800 dark:text-slate-200">Cơ chế chia sẻ thông minh (Alias)</span>
            <p className="text-muted-foreground text-xs leading-relaxed">
              Bạn có thể tạo một Alias (liên kết chia sẻ) của tài liệu gốc cho phòng ban khác. Hệ thống tối ưu hóa dung lượng lưu trữ bằng cách liên kết tới file gốc và chặn tuyệt đối việc chia sẻ bắc cầu (alias của alias).
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
};
