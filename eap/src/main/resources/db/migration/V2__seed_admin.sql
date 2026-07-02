INSERT INTO users (id, username, email, password_hash, role, department_id)
VALUES (
    '00000000-0000-0000-0000-000000000000', 
    'admin', 
    'admin@vccorp.vn', 
    '$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju', -- BCrypt for '123456'
    'SYSTEM_ADMIN', 
    NULL
);
