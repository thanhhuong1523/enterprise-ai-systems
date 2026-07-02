CREATE TABLE departments (
    id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    department_id UUID REFERENCES departments(id),
    CONSTRAINT chk_user_department_integrity CHECK (
        (role = 'SYSTEM_ADMIN' AND department_id IS NULL) OR
        (role <> 'SYSTEM_ADMIN' AND department_id IS NOT NULL)
    )
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    business_code VARCHAR(50) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_reference VARCHAR(500),
    file_size BIGINT,
    hash VARCHAR(64),
    owner_department_id UUID REFERENCES departments(id) NOT NULL,
    parent_id UUID REFERENCES documents(id),
    creator_department_id UUID REFERENCES departments(id),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    deleted_at TIMESTAMP,
    CONSTRAINT chk_document_type_integrity CHECK (
        (parent_id IS NULL AND file_reference IS NOT NULL AND creator_department_id IS NULL) OR
        (parent_id IS NOT NULL AND file_reference IS NULL AND creator_department_id IS NOT NULL)
    )
);

-- Unique index for active aliases per department
CREATE UNIQUE INDEX uq_active_alias_per_dept 
ON documents (parent_id, owner_department_id) 
WHERE parent_id IS NOT NULL AND deleted_at IS NULL;

-- Indexes for performance
CREATE INDEX idx_doc_owner_dept ON documents (owner_department_id);
CREATE INDEX idx_doc_creator_dept ON documents (creator_department_id);
