import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { ProtectedRoute } from '@/routes/ProtectedRoute';
import { DashboardLayout } from '@/layouts/DashboardLayout';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardOverviewPage } from '@/pages/DashboardOverviewPage';
import { DepartmentsPage } from '@/pages/DepartmentsPage';
import { UsersPage } from '@/pages/UsersPage';
import { DocumentsPage } from '@/pages/DocumentsPage';
import { SharedDocumentsPage } from '@/pages/SharedDocumentsPage';
import { NotFoundPage } from '@/pages/NotFoundPage';

export const AppRoutes: React.FC = () => {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <DashboardLayout />
            </ProtectedRoute>
          }
        >
          <Route index element={<DashboardOverviewPage />} />
          
          <Route
            path="departments"
            element={
              <ProtectedRoute allowedRoles={['SYSTEM_ADMIN']}>
                <DepartmentsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="users"
            element={
              <ProtectedRoute allowedRoles={['SYSTEM_ADMIN']}>
                <UsersPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="documents"
            element={
              <ProtectedRoute allowedRoles={['ROLE_EMPLOYEE', 'ROLE_DEPT_MANAGER', 'ROLE_BOARD']}>
                <DocumentsPage />
              </ProtectedRoute>
            }
          />
          <Route
            path="shared-documents"
            element={
              <ProtectedRoute allowedRoles={['ROLE_EMPLOYEE', 'ROLE_DEPT_MANAGER', 'ROLE_BOARD']}>
                <SharedDocumentsPage />
              </ProtectedRoute>
            }
          />
        </Route>
        
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
};
