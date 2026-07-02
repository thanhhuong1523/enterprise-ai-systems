-- Insert admin1 (SYSTEM_ADMIN)
INSERT INTO users (id, username, email, password_hash, role, department_id, full_name, phone, created_at)
VALUES (
    CAST('22222222-2222-2222-2222-222222222222' AS UUID),
    'admin1',
    'admin1@vccorp.vn',
    '$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju', -- BCrypt for '123456'
    'SYSTEM_ADMIN',
    NULL,
    'Trần Văn Bình',
    '0981111111',
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- Insert admin2 (SYSTEM_ADMIN)
INSERT INTO users (id, username, email, password_hash, role, department_id, full_name, phone, created_at)
VALUES (
    CAST('33333333-3333-3333-3333-333333333333' AS UUID),
    'admin2',
    'admin2@vccorp.vn',
    '$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju', -- BCrypt for '123456'
    'SYSTEM_ADMIN',
    NULL,
    'Phạm Thị Mai',
    '0982222222',
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;
