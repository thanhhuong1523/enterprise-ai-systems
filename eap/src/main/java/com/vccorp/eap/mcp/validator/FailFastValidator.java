package com.vccorp.eap.mcp.validator;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.mcp.registry.ToolCatalogRegistry;
import com.vccorp.eap.mcp.registry.ToolInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Thẩm định ngắt sớm dữ liệu thiếu/rỗng từ kết quả gọi công cụ để bảo vệ tính toàn vẹn hệ thống (BR-2, Fail-Fast).
 * Thiết kế Generic: Không hardcode bất kỳ tên hàm/công cụ nào trong mã nguồn, nạp động từ ToolCatalogRegistry.
 */
@Component
public class FailFastValidator {

    private final ToolCatalogRegistry toolCatalogRegistry;

    @Autowired
    public FailFastValidator(@Autowired(required = false) ToolCatalogRegistry toolCatalogRegistry) {
        this.toolCatalogRegistry = toolCatalogRegistry;
    }

    public FailFastValidator() {
        this(null);
    }

    public void validateToolExecutionResult(String toolName, Object result, Map<String, Object> arguments) {
        if (toolName == null || toolName.trim().isEmpty()) {
            return;
        }

        boolean isEmptyOrNull = (result == null)
                || (result instanceof Optional<?> opt && (opt.isEmpty() || opt.get() == null));

        if (!isEmptyOrNull) {
            return;
        }

        // Truy xuất metadata và label cấu hình động từ ToolCatalogRegistry
        Optional<ToolInfo> toolInfoOpt = (toolCatalogRegistry != null)
                ? toolCatalogRegistry.findByName(toolName)
                : Optional.empty();

        if (toolInfoOpt.isPresent()) {
            ToolInfo info = toolInfoOpt.get();
            String template = info.notFoundLabel();
            String errorCodeName = info.errorCode();

            ErrorCode errorCode = parseErrorCode(errorCodeName);
            String message = (template != null && !template.trim().isEmpty())
                    ? toolCatalogRegistry.renderLabel(template, arguments)
                    : "Không tìm thấy kết quả phù hợp từ công cụ '" + toolName + "'.";

            throw new BusinessException(errorCode, message);
        }

        // Fallback an toàn nếu chưa nạp catalog
        String target = (arguments != null && arguments.get("name") != null)
                ? arguments.get("name").toString().trim()
                : "";
        if (!target.isEmpty() && "getDepartmentByName".equalsIgnoreCase(toolName)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND,
                    "Không tìm thấy phòng ban '" + target + "' trong hệ thống. Vui lòng kiểm tra lại tên phòng ban.");
        }

        throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR,
                "Không thể khởi tạo hoặc hoàn tất tác vụ của công cụ '" + toolName + "'.");
    }

    private ErrorCode parseErrorCode(String codeName) {
        if (codeName != null && !codeName.trim().isEmpty()) {
            try {
                return ErrorCode.valueOf(codeName.trim());
            } catch (IllegalArgumentException ignored) {
            }
        }
        return ErrorCode.ERR_SYSTEM_ERROR;
    }
}
