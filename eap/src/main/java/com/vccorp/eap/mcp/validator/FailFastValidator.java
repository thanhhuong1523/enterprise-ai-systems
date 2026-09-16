package com.vccorp.eap.mcp.validator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Thẩm định ngắt sớm dữ liệu thiếu/rỗng từ kết quả gọi công cụ để bảo vệ tính toàn vẹn hệ thống (BR-2, Fail-Fast).
 * Thiết kế Generic: Không hardcode bất kỳ tên hàm/công cụ nào trong mã nguồn.
 */
@Component
public class FailFastValidator {

    public void validateToolExecutionResult(String toolName, Object result, Map<String, Object> arguments) {
        if (toolName == null) {
            return;
        }

        if (result == null || (result instanceof Optional<?> opt && opt.isEmpty())) {
            String target = (arguments != null && arguments.get("name") != null)
                    ? arguments.get("name").toString()
                    : "";

            if (!target.isEmpty()) {
                throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND,
                        "Không tìm thấy phòng ban '" + target + "' trong hệ thống. Vui lòng kiểm tra lại tên phòng ban.");
            }

            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Không thể khởi tạo hoặc hoàn tất tác vụ của công cụ.");
        }
    }
}
