-- V8: Fix user1 department assignment
-- Vấn đề: user1 có role ROLE_BOARD nhưng department_id trỏ vào phòng HR
-- Theo nghiệp vụ: ROLE_BOARD phải thuộc phòng BOARD (Ban Giám Đốc)
--
-- Fix: chuyển user1's department_id sang BOARD dept

DO $$
DECLARE
    v_board_dept_id UUID;
    v_user1_id      UUID;
BEGIN
    SELECT id INTO v_board_dept_id FROM departments WHERE code = 'BOARD' LIMIT 1;
    SELECT id INTO v_user1_id      FROM users       WHERE username = 'user1' LIMIT 1;

    IF v_user1_id IS NOT NULL AND v_board_dept_id IS NOT NULL THEN
        UPDATE users
        SET department_id = v_board_dept_id
        WHERE id   = v_user1_id
          AND role = 'ROLE_BOARD';       -- chỉ sửa khi role vẫn là BOARD
    END IF;
END $$;
