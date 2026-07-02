import React, { Component, ErrorInfo, ReactNode } from 'react';
import { ShieldAlert } from 'lucide-react';
import { Button } from './Button';

interface Props {
  children?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null
  };

  public static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Uncaught error:", error, errorInfo);
  }

  public render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center p-6 bg-background text-foreground">
          <div className="flex flex-col items-center text-center max-w-md gap-4">
            <div className="p-4 bg-rose-500/10 border border-rose-500/20 text-rose-500 rounded-2xl">
              <ShieldAlert className="w-12 h-12" />
            </div>
            <h1 className="text-2xl font-bold">Đã xảy ra lỗi hệ thống</h1>
            <p className="text-sm text-muted-foreground">
              Rất tiếc, đã có sự cố xảy ra khi tải trang này. Vui lòng tải lại trang hoặc liên hệ quản trị viên.
            </p>
            <Button
              variant="primary"
              onClick={() => {
                this.setState({ hasError: false });
                window.location.reload();
              }}
              className="mt-2"
            >
              Tải lại trang
            </Button>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}
