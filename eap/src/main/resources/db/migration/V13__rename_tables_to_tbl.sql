-- Migration V13: Rename tables to standard tbl_ prefix
ALTER TABLE departments RENAME TO tbl_departments;
ALTER TABLE users RENAME TO tbl_users;
ALTER TABLE documents RENAME TO tbl_documents;
