import React from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import { HelpCircle, Trash2, LogOut, Share2, ShieldAlert } from 'lucide-react';

interface ConfirmDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void | Promise<void>;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  isLoading?: boolean;
  variant?: 'danger' | 'primary' | 'warning' | 'success';
}

export const ConfirmDialog: React.FC<ConfirmDialogProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel,
  cancelLabel = 'Huỷ',
  isLoading = false,
  variant = 'primary',
}) => {
  const getIcon = () => {
    switch (variant) {
      case 'danger':
        return <Trash2 className="w-8 h-8 text-rose-500" />;
      case 'warning':
        return <ShieldAlert className="w-8 h-8 text-amber-500" />;
      case 'success':
        return <Share2 className="w-8 h-8 text-emerald-500" />;
      default:
        return <HelpCircle className="w-8 h-8 text-primary" />;
    }
  };

  const getConfirmButtonClass = () => {
    switch (variant) {
      case 'danger':
        return 'bg-rose-500 hover:bg-rose-600 text-white border-0';
      case 'warning':
        return 'bg-amber-500 hover:bg-amber-600 text-white border-0';
      case 'success':
        return 'bg-emerald-500 hover:bg-emerald-600 text-white border-0';
      default:
        return 'bg-primary hover:bg-primary/90 text-white border-0';
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title}>
      <div className="flex flex-col gap-4 text-xs">
        <div className="flex items-start gap-4">
          <div className="flex-shrink-0 p-2 bg-slate-50 dark:bg-slate-900 rounded-xl">
            {getIcon()}
          </div>
          <div className="flex-grow pt-1">
            <p className="text-slate-600 dark:text-slate-400 leading-relaxed text-[13px] font-medium">
              {message}
            </p>
          </div>
        </div>

        <div className="flex justify-end gap-3 mt-4 pt-4 border-t border-border/60">
          <Button
            type="button"
            variant="outline"
            onClick={onClose}
            disabled={isLoading}
            className="h-9 px-4 rounded-xl text-xs font-semibold"
          >
            {cancelLabel}
          </Button>
          <Button
            type="button"
            onClick={onConfirm}
            loading={isLoading}
            className={`h-9 px-4 rounded-xl text-xs font-semibold ${getConfirmButtonClass()}`}
          >
            {confirmLabel}
          </Button>
        </div>
      </div>
    </Modal>
  );
};
