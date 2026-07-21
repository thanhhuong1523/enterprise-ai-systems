-- Sequence for atomic business code generation under high concurrency
CREATE SEQUENCE IF NOT EXISTS doc_business_code_seq START WITH 100000 INCREMENT BY 1;

-- Partial Unique Index for active original documents deduplication per department
CREATE UNIQUE INDEX IF NOT EXISTS uq_documents_hash_dept 
ON documents (hash, owner_department_id) 
WHERE deleted_at IS NULL;

-- Global B-Tree Index on hash for Single Instance Storage (SIS) lookup across all departments
CREATE INDEX IF NOT EXISTS idx_documents_hash 
ON documents (hash);
