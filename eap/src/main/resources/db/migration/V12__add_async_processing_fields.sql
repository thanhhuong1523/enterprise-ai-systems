-- Migration V12: Add async processing columns and constraints
ALTER TABLE documents ADD COLUMN status VARCHAR(50);
ALTER TABLE documents ADD COLUMN worker_id VARCHAR(50);
ALTER TABLE documents ADD COLUMN retry_count INT;
ALTER TABLE documents ADD COLUMN last_completed_chunk INT;
ALTER TABLE documents ADD COLUMN total_chunks INT;

-- Apply defaults to any existing original documents (though table is currently empty, this is safe practice)
UPDATE documents 
SET status = 'COMPLETED',
    retry_count = 0,
    total_chunks = 0,
    last_completed_chunk = 0
WHERE parent_id IS NULL;

-- Add check constraint chk_alias_nullable
ALTER TABLE documents ADD CONSTRAINT chk_alias_nullable CHECK (
  (parent_id IS NULL AND status IS NOT NULL AND retry_count IS NOT NULL AND total_chunks IS NOT NULL) OR
  (parent_id IS NOT NULL AND status IS NULL AND retry_count IS NULL AND total_chunks IS NULL AND last_completed_chunk IS NULL AND worker_id IS NULL)
);

-- Add indexes for ready and processing tasks
CREATE INDEX idx_documents_status_ready 
ON documents (created_at ASC) 
WHERE status = 'READY' AND deleted_at IS NULL;

CREATE INDEX idx_documents_status_processing 
ON documents (id) 
WHERE status = 'PROCESSING' AND deleted_at IS NULL;
