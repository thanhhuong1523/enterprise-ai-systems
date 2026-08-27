-- Migration V16: Thay thế GIN index mặc định bằng jsonb_path_ops để hỗ trợ toán tử @>
-- và thêm B-tree index cho doc_type để tối ưu single-value filter

-- 1. Xóa GIN index cũ (dùng ops mặc định, không hỗ trợ tối ưu @>)
DROP INDEX IF EXISTS idx_chunks_metadata_gin;

-- 2. B-tree index cho single-value key doc_type (hỗ trợ lọc nhanh bằng =)
CREATE INDEX idx_meta_doc_type ON tbl_chunks ((metadata->>'doc_type'));

-- 3. GIN jsonb_path_ops index cho tất cả array keys (chunk_role, entities, time_refs, heading)
--    jsonb_path_ops nhỏ hơn và nhanh hơn ops mặc định với toán tử @>
CREATE INDEX idx_meta_gin ON tbl_chunks USING GIN (metadata jsonb_path_ops);
