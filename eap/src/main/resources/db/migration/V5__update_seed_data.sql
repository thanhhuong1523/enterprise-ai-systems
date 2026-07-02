DO $$
BEGIN
    -- Update system admin details in place
    UPDATE users 
    SET full_name = 'Admin System', phone = '0988888888' 
    WHERE username = 'admin';

    -- Check if Board department exists by ID or Code
    IF NOT EXISTS (
        SELECT 1 FROM departments 
        WHERE id = CAST('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1' AS UUID) 
           OR code = 'BOARD'
    ) THEN
        INSERT INTO departments (id, code, name, description, created_at)
        VALUES (
            CAST('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1' AS UUID),
            'BOARD',
            'Ban Giám Đốc',
            'Ban Giám Đốc điều hành hệ thống EAP.',
            CURRENT_TIMESTAMP
        );
    ELSE
        UPDATE departments 
        SET name = 'Ban Giám Đốc', 
            description = 'Ban Giám Đốc điều hành hệ thống EAP.' 
        WHERE id = CAST('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1' AS UUID) 
           OR code = 'BOARD';
    END IF;

    -- Check if user1 exists by ID, username, or email
    IF NOT EXISTS (
        SELECT 1 FROM users 
        WHERE id = CAST('11111111-1111-1111-1111-111111111111' AS UUID)
           OR username = 'user1'
           OR email = 'user1@vccorp.vn'
    ) THEN
        INSERT INTO users (id, username, email, password_hash, role, department_id, full_name, phone, created_at)
        VALUES (
            CAST('11111111-1111-1111-1111-111111111111' AS UUID),
            'user1',
            'user1@vccorp.vn',
            '$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju', -- BCrypt for '123456'
            'ROLE_BOARD',
            CAST('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1' AS UUID),
            'Nguyễn Văn A',
            '0912345678',
            CURRENT_TIMESTAMP
        );
    ELSE
        UPDATE users 
        SET username = 'user1',
            email = 'user1@vccorp.vn',
            password_hash = '$2a$10$JWvOb7f1lScBWmsRUrPtk..X24ZBG1DA6izRmjDEAsXkIA1E4oCju',
            role = 'ROLE_BOARD',
            department_id = CAST('d1d1d1d1-d1d1-d1d1-d1d1-d1d1d1d1d1d1' AS UUID),
            full_name = 'Nguyễn Văn A',
            phone = '0912345678'
        WHERE id = CAST('11111111-1111-1111-1111-111111111111' AS UUID)
           OR username = 'user1'
           OR email = 'user1@vccorp.vn';
    END IF;
END $$;
