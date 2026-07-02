import React from 'react';

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
}

export const Card: React.FC<CardProps> = ({ children, className = '', ...props }) => {
  return (
    <div
      className={`bg-card text-card-foreground border border-border/80 shadow-sm rounded-2xl backdrop-blur-sm transition-all duration-300 ${className}`}
      {...props}
    >
      {children}
    </div>
  );
};
