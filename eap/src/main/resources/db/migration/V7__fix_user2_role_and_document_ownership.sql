-- V7: Fix user2 data và document ownership
-- Vấn đề: user2 bị assign role ROLE_BOARD và department BOARD
-- Điều này vi phạm nghiệp vụ: BOARD không được upload tài liệu
-- và tài liệu của BOARD không thể tạo alias (BOARD protection rule)
--
-- Fix:
-- 1. Chuyển user2 sang ROLE_DEPT_MANAGER thuộc phòng RND (Phát triển)
-- 2. Fix tất cả tài liệu đang bị gán nhầm owner_department_id = BOARD
--    do user2 upload trước khi được sửa, chuyển về phòng của người tạo thực tế.

DO $$
DECLARE
    v_rnd_dept_id UUID;
    v_board_dept_id UUID;
    v_user2_id UUID;
BEGIN
    -- Lấy ID phòng RND và BOARD
    SELECT id INTO v_rnd_dept_id   FROM departments WHERE code = 'RND'   LIMIT 1;
    SELECT id INTO v_board_dept_id FROM departments WHERE code = 'BOARD' LIMIT 1;

    -- Lấy ID của user2
    SELECT id INTO v_user2_id FROM users WHERE username = 'user2' LIMIT 1;

    -- Chỉ thực hiện nếu user2 tồn tại và phòng RND tồn tại
    IF v_user2_id IS NOT NULL AND v_rnd_dept_id IS NOT NULL THEN

        -- Fix role và department của user2
        UPDATE users
        SET role          = 'ROLE_DEPT_MANAGER',
            department_id = v_rnd_dept_id
        WHERE id = v_user2_id
          AND role = 'ROLE_BOARD'; -- chỉ sửa nếu vẫn còn sai

        -- Fix các tài liệu gốc (parent_id IS NULL) do user2 tạo
        -- mà đang bị gán owner = BOARD dept → chuyển về RND
        IF v_board_dept_id IS NOT NULL THEN
            UPDATE documents
            SET owner_department_id = v_rnd_dept_id
            WHERE created_by          = v_user2_id
              AND parent_id           IS NULL
              AND owner_department_id  = v_board_dept_id
              AND deleted_at           IS NULL;
        END IF;

    END IF;
END $$;
