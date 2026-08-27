-- Migration V15: Add skipped_chunks_count column to tbl_documents
ALTER TABLE tbl_documents ADD COLUMN skipped_chunks_count INT DEFAULT 0 NOT NULL;
