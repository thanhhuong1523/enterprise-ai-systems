import React, { useState } from 'react';
import { useAuth } from '@/store/AuthContext';
import { useTheme } from '@/store/ThemeContext';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import {
  Users,
  Building2,
  LogOut,
  Sun,
  Moon,
  FolderLock,
  LayoutDashboard,
  FileText,
  Share2,
  Menu,
  ChevronLeft,
  ChevronRight
} from 'lucide-react';
import { Button } from '@/components/Button';
import { useDepartments } from '@/hooks/useDepartments';
import { ConfirmDialog } from '@/components/ConfirmDialog';

export const DashboardLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { departments } = useDepartments();

  const [showLogoutConfirm, setShowLogoutConfirm] = useState(false);

  const userDept = departments.find((d) => d.id === user?.departmentId);
  const userDeptName = user?.role === 'SYSTEM_ADMIN' ? 'Quản trị hệ thống' : (userDept ? `${userDept.name} (${userDept.code})` : 'Đang tải...');

  const [isCollapsed, setIsCollapsed] = React.useState(() => {
    return localStorage.getItem('sidebar-collapsed') === 'true';
  });
  const [isMobileOpen, setIsMobileOpen] = React.useState(false);

  const toggleCollapse = () => {
    setIsCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem('sidebar-collapsed', String(next));
      return next;
    });
  };

  const executeLogout = () => {
    queryClient.clear();
    logout();
    navigate('/login');
  };

  const isAdmin = user?.role === 'SYSTEM_ADMIN';

  const menuItems = isAdmin
    ? [
        { label: 'Phòng ban', path: '/departments', icon: Building2 },
        { label: 'Người dùng', path: '/users', icon: Users },
      ]
    : [
        { label: 'Tài liệu gốc', path: '/documents', icon: FileText },
        { label: 'Tài liệu chia sẻ', path: '/shared-documents', icon: Share2 },
      ];

  const getBreadcrumbs = () => {
    const paths = location.pathname.split('/').filter(Boolean);
    if (paths.length === 0) return [{ label: 'Tổng quan', path: '/' }];
    
    return [
      { label: 'Tổng quan', path: '/' },
      ...paths.map((p, idx) => {
        const path = `/${paths.slice(0, idx + 1).join('/')}`;
        let label = p;
        if (p === 'departments') label = 'Phòng ban';
        if (p === 'users') label = 'Người dùng';
        if (p === 'documents') label = 'Tài liệu';
        if (p === 'shared-documents') label = 'Tài liệu chia sẻ';
        return { label, path };
      }),
    ];
  };

  const breadcrumbs = getBreadcrumbs();

  const sidebarContent = (isDrawer = false) => {
    const showFull = isDrawer || !isCollapsed;

    return (
      <div className="flex flex-col h-full justify-between">
        <div>
          {/* Logo Section */}
          <div className="h-16 flex items-center gap-3 px-6 border-b border-border/80 relative">
            <div className="p-2 bg-primary/10 rounded-xl text-primary flex-shrink-0">
              <FolderLock className="w-6 h-6" />
            </div>
            {showFull && (
              <div className="overflow-hidden whitespace-nowrap">
                <h1 className="text-sm font-bold tracking-tight">VCC-EAP</h1>
                <span className="text-[10px] text-muted-foreground uppercase font-semibold">Knowledge Storage</span>
              </div>
            )}
            
            {/* Desktop Collapse Toggle Inside Sidebar Header */}
            {!isDrawer && (
              <button
                onClick={toggleCollapse}
                className="hidden md:flex absolute -right-3 top-1/2 -translate-y-1/2 w-6 h-6 bg-card border border-border rounded-full items-center justify-center text-muted-foreground hover:text-foreground hover:bg-secondary transition-all"
              >
                {isCollapsed ? <ChevronRight className="w-3.5 h-3.5" /> : <ChevronLeft className="w-3.5 h-3.5" />}
              </button>
            )}
          </div>

          {/* Navigation Links */}
          <nav className="p-4 flex flex-col gap-1.5">
            <Link
              to="/"
              className={`flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                location.pathname === '/'
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'hover:bg-secondary text-muted-foreground hover:text-foreground'
              } ${!showFull ? 'justify-center px-2' : ''}`}
              title={!showFull ? 'Tổng quan' : undefined}
              onClick={() => isDrawer && setIsMobileOpen(false)}
            >
              <LayoutDashboard className="w-5 h-5 flex-shrink-0" />
              {showFull && <span>Tổng quan</span>}
            </Link>
            
            {menuItems.map((item) => {
              const Icon = item.icon;
              const isActive = location.pathname.startsWith(item.path);
              return (
                <Link
                  key={item.path}
                  to={item.path}
                  className={`flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                    isActive
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'hover:bg-secondary text-muted-foreground hover:text-foreground'
                  } ${!showFull ? 'justify-center px-2' : ''}`}
                  title={!showFull ? item.label : undefined}
                  onClick={() => isDrawer && setIsMobileOpen(false)}
                >
                  <Icon className="w-5 h-5 flex-shrink-0" />
                  {showFull && <span>{item.label}</span>}
                </Link>
              );
            })}
          </nav>
        </div>

        {/* User profile / Department footer */}
        <div className="p-4 border-t border-border/80 flex flex-col gap-3">
          <div className={`flex items-center gap-3 ${showFull ? 'px-2' : 'justify-center'}`}>
            <div className="w-10 h-10 rounded-full bg-primary/10 text-primary flex items-center justify-center font-bold text-sm flex-shrink-0">
              {(user?.fullName || user?.username || 'U').charAt(0).toUpperCase()}
            </div>
            {showFull && (
              <div className="overflow-hidden">
                <h4 className="text-xs font-semibold truncate">{user?.fullName || user?.username}</h4>
                <p className="text-[10px] text-muted-foreground truncate uppercase font-bold tracking-wider">
                  {user?.role.replace('ROLE_', '')}
                </p>
              </div>
            )}
          </div>
          {showFull && (
            <div className="px-2 py-1.5 bg-slate-50 dark:bg-slate-900/50 rounded-xl border border-border/60">
              <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider block">Phòng ban</span>
              <span className="text-xs font-semibold text-slate-700 dark:text-slate-300 truncate block" title={userDeptName}>
                {userDeptName}
              </span>
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div className="flex h-screen bg-background text-foreground overflow-hidden">
      {/* Mobile Drawer (Hidden on Desktop) */}
      {isMobileOpen && (
        <div
          className="fixed inset-0 z-40 bg-black/40 backdrop-blur-sm md:hidden transition-all duration-300"
          onClick={() => setIsMobileOpen(false)}
        />
      )}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-card border-r border-border/80 flex flex-col md:hidden transition-transform duration-300 transform ${
          isMobileOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {sidebarContent(true)}
      </aside>

      {/* Desktop Sidebar (Hidden on Mobile) */}
      <aside
        className={`hidden md:flex flex-col border-r border-border/80 bg-card/60 backdrop-blur-md flex-shrink-0 transition-all duration-300 ${
          isCollapsed ? 'w-20' : 'w-64'
        }`}
      >
        {sidebarContent(false)}
      </aside>

      {/* Main Content Area */}
      <div className="flex-grow flex flex-col overflow-hidden w-full">
        {/* Header */}
        <header className="h-16 border-b border-border/80 bg-card/40 backdrop-blur-md flex items-center justify-between px-8 flex-shrink-0 gap-4">
          <div className="flex items-center gap-4">
            {/* Hamburger Trigger button */}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setIsMobileOpen(true)}
              className="md:hidden w-10 h-10 p-0 rounded-xl"
            >
              <Menu className="w-5 h-5" />
            </Button>

            <div className="flex flex-col overflow-hidden">
              {/* Dynamic Breadcrumbs */}
              <div className="flex items-center gap-1.5 text-[10px] text-muted-foreground font-medium uppercase tracking-wider mb-0.5 truncate max-w-full">
                {breadcrumbs.map((bc, idx) => (
                  <React.Fragment key={bc.path}>
                    {idx > 0 && <span className="text-[8px] opacity-60">/</span>}
                    {idx === breadcrumbs.length - 1 ? (
                      <span className="text-foreground font-semibold truncate">{bc.label}</span>
                    ) : (
                      <Link to={bc.path} className="hover:text-foreground transition-colors shrink-0">
                        {bc.label}
                      </Link>
                    )}
                  </React.Fragment>
                ))}
              </div>
              <h2 className="text-sm font-bold text-slate-800 dark:text-slate-200 truncate">
                {location.pathname === '/' && 'Tổng quan hệ thống'}
                {location.pathname.startsWith('/departments') && 'Quản lý phòng ban'}
                {location.pathname.startsWith('/users') && 'Quản lý nhân sự'}
                {location.pathname.startsWith('/documents') && 'Quản lý tài liệu'}
                {location.pathname.startsWith('/shared-documents') && 'Tài liệu chia sẻ'}
              </h2>
            </div>
          </div>

          <div className="flex items-center gap-3 flex-shrink-0">
            {/* Theme Toggle Button */}
            <Button
              variant="outline"
              size="sm"
              onClick={toggleTheme}
              className="w-10 h-10 p-0 rounded-xl"
              title="Thay đổi giao diện"
            >
              {theme === 'light' ? <Moon className="w-5 h-5" /> : <Sun className="w-5 h-5" />}
            </Button>

            {/* Logout Button */}
            <Button
              variant="outline"
              onClick={() => setShowLogoutConfirm(true)}
              className="h-10 rounded-xl text-rose-500 hover:text-rose-600 hover:bg-rose-500/10 border-rose-200 dark:border-rose-950/30 gap-2 px-4 flex items-center"
              title="Đăng xuất"
            >
              <LogOut className="w-4 h-4 flex-shrink-0" />
              <span className="font-semibold text-xs">Đăng xuất</span>
            </Button>
          </div>
        </header>

        {/* Page Content View */}
        <main className="flex-grow p-4 md:p-8 overflow-y-auto bg-slate-50 dark:bg-slate-950/20">
          <Outlet />
        </main>
      </div>

      {/* Shared Logout Confirmation */}
      <ConfirmDialog
        isOpen={showLogoutConfirm}
        onClose={() => setShowLogoutConfirm(false)}
        onConfirm={executeLogout}
        title="Xác nhận đăng xuất"
        message="Bạn có chắc chắn muốn đăng xuất?"
        confirmLabel="Đăng xuất"
        variant="danger"
      />
    </div>
  );
};
