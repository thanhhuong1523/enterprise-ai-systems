import React, { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useAuth } from '@/store/AuthContext';
import { useToast } from '@/components/Toast';
import { useNavigate } from 'react-router-dom';
import { authService } from '@/services/api';
import { Button } from '@/components/Button';
import { Input } from '@/components/Input';
import { Card } from '@/components/Card';
import { FolderLock } from 'lucide-react';

const loginSchema = z.object({
  username: z.string().min(1, 'Tên đăng nhập không được để trống'),
  password: z.string().min(1, 'Mật khẩu không được để trống'),
});

type LoginSchema = z.infer<typeof loginSchema>;

export const LoginPage: React.FC = () => {
  const { login } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginSchema>({
    resolver: zodResolver(loginSchema),
  });

  const onSubmit = async (data: LoginSchema) => {
    setLoading(true);
    try {
      const response = await authService.login(data);
      if (response.success && response.data) {
        login(
          response.data.accessToken,
          response.data.userInfo
        );
        showToast('Đăng nhập thành công', 'success');
        navigate('/');
      } else {
        showToast(response.message || 'Đăng nhập thất bại', 'error');
      }
    } catch (err: any) {
      const msg = err.response?.data?.message || 'Tên đăng nhập hoặc mật khẩu không chính xác.';
      showToast(msg, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-slate-50 dark:bg-slate-950/20 text-foreground transition-all duration-300">
      <div className="absolute inset-0 bg-grid-pattern opacity-[0.02] dark:opacity-[0.01]" />
      
      {/* Decorative gradient blur */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] bg-primary/20 dark:bg-primary/10 rounded-full blur-[120px] pointer-events-none" />

      <Card className="w-full max-w-md p-8 flex flex-col gap-6 relative z-10">
        <div className="flex flex-col items-center gap-3 text-center">
          <div className="p-3 bg-primary/10 rounded-2xl text-primary">
            <FolderLock className="w-8 h-8" />
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight">Đăng nhập hệ thống</h1>
            <p className="text-xs text-muted-foreground mt-1">VCC Enterprise Archive Platform</p>
          </div>
        </div>

        <form onSubmit={handleSubmit(onSubmit)} className="flex flex-col gap-4">
          <Input
            label="Tên đăng nhập"
            placeholder="Nhập tên đăng nhập..."
            error={errors.username?.message}
            {...register('username')}
          />
          <Input
            label="Mật khẩu"
            type="password"
            placeholder="Nhập mật khẩu..."
            error={errors.password?.message}
            {...register('password')}
          />

          <Button type="submit" loading={loading} className="w-full h-11 mt-2">
            Đăng nhập
          </Button>
        </form>
      </Card>
    </div>
  );
};
