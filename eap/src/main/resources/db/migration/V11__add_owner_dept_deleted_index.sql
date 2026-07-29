-- Index to optimize department-level list retrieval of active documents (§2.2, §5.10)
CREATE INDEX IF NOT EXISTS idx_documents_owner_dept_deleted 
ON documents (owner_department_id, deleted_at);
