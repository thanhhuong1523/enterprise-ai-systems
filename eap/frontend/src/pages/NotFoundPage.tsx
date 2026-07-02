import React from 'react';
import { FileQuestion } from 'lucide-react';
import { Button } from '@/components/Button';
import { useNavigate } from 'react-router-dom';

export const NotFoundPage: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-background text-foreground">
      <div className="flex flex-col items-center text-center max-w-md gap-4">
        <div className="p-4 bg-blue-500/10 border border-blue-500/20 text-blue-500 rounded-2xl">
          <FileQuestion className="w-12 h-12" />
        </div>
        <h1 className="text-3xl font-extrabold tracking-tight">404 - Không tìm thấy trang</h1>
        <p className="text-sm text-muted-foreground">
          Đường dẫn bạn yêu cầu không tồn tại hoặc đã bị di chuyển. Vui lòng quay lại trang chủ.
        </p>
        <Button
          variant="primary"
          onClick={() => navigate('/')}
          className="mt-2"
        >
          Quay lại trang chủ
        </Button>
      </div>
    </div>
  );
};
