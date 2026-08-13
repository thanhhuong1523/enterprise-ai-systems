-- Migration V14: Enable pgvector extension and create tbl_chunks table with metadata JSONB column
CREATE EXTENSION IF NOT EXISTS vector;

-- 1. Create tbl_chunks table
CREATE TABLE tbl_chunks (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1024),
    metadata JSONB DEFAULT '{}'::jsonb NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_chunks_document FOREIGN KEY (document_id) REFERENCES tbl_documents(id) ON DELETE CASCADE,
    CONSTRAINT uq_document_chunk UNIQUE (document_id, chunk_index)
);

-- 2. Foreign key index for document_id
CREATE INDEX idx_chunks_document_id ON tbl_chunks(document_id);

-- 3. HNSW index for vector cosine similarity
CREATE INDEX idx_chunks_embedding_hnsw ON tbl_chunks USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 4. GIN Index on metadata JSONB column for efficient JSON operations & future Metadata Filtering
CREATE INDEX idx_chunks_metadata_gin ON tbl_chunks USING gin (metadata);

-- 5. Index on tbl_documents parent_id for alias resolution optimization
CREATE INDEX IF NOT EXISTS idx_documents_parent_id ON tbl_documents(parent_id)
WHERE parent_id IS NOT NULL AND deleted_at IS NULL;
