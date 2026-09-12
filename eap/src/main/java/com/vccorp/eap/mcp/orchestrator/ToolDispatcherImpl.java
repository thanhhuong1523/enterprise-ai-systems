package com.vccorp.eap.mcp.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.mcp.annotation.McpTool;
import com.vccorp.eap.mcp.annotation.McpToolParam;
import com.vccorp.eap.mcp.tools.DepartmentTools;
import com.vccorp.eap.mcp.tools.DocumentTools;
import com.vccorp.eap.mcp.tools.McpToolFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Cài đặt tự động hoá hoàn toàn của ToolDispatcher (KISS Principle).
 * - Tự động phát hiện và đăng ký mọi công cụ từ List&lt;McpToolFacade&gt; mà không cần hardcode tên hàm hay if-else.
 * - Tự động ánh xạ tham số động (Dynamic Argument Resolution) sang đúng kiểu dữ liệu với Jackson ObjectMapper.
 * - Tự động thẩm định tham số bắt buộc (Fail-Fast) theo cấu hình của @McpToolParam(required = true).
 */
@Service
public class ToolDispatcherImpl implements ToolDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ToolDispatcherImpl.class);
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final Map<String, Function<Map<String, Object>, Object>> handlers = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    /**
     * Constructor chính thức: Tự động gom toàn bộ Bean triển khai McpToolFacade từ Spring Context.
     */
    @Autowired
    public ToolDispatcherImpl(@Autowired(required = false) List<McpToolFacade> toolFacades, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        registerToolFacades(toolFacades);
    }

    /**
     * Constructor tương thích ngược cho unit test truyền trực tiếp DepartmentTools và DocumentTools.
     */
    public ToolDispatcherImpl(DepartmentTools departmentTools, DocumentTools documentTools) {
        this(departmentTools, documentTools, new ObjectMapper());
    }

    public ToolDispatcherImpl(DepartmentTools departmentTools, DocumentTools documentTools, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        registerToolFacades(List.of(departmentTools, documentTools));
    }

    private void registerToolFacades(List<McpToolFacade> toolFacades) {
        if (toolFacades == null) {
            return;
        }

        for (McpToolFacade facade : toolFacades) {
            if (facade == null) {
                continue;
            }
            Class<?> clazz = facade.getClass();
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(McpTool.class)) {
                    McpTool mcpTool = method.getAnnotation(McpTool.class);
                    String toolName = (mcpTool.name() != null && !mcpTool.name().trim().isEmpty())
                            ? mcpTool.name().trim()
                            : method.getName();

                    register(toolName, args -> invokeDynamicMethod(facade, method, args));
                    log.info("[ToolDispatcher] Đã tự động đăng ký công cụ '{}' từ bean '{}'", toolName, clazz.getSimpleName());
                }
            }
        }
    }

    private Object invokeDynamicMethod(Object targetBean, Method method, Map<String, Object> arguments) {
        Parameter[] parameters = method.getParameters();
        String[] paramNames = PARAM_NAME_DISCOVERER.getParameterNames(method);
        Object[] resolvedArgs = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            McpToolParam paramAnno = param.getAnnotation(McpToolParam.class);

            String paramName = (paramAnno != null && !paramAnno.name().isEmpty())
                    ? paramAnno.name().trim()
                    : ((paramNames != null && i < paramNames.length && paramNames[i] != null) ? paramNames[i] : param.getName());

            boolean isRequired = paramAnno == null || paramAnno.required();
            Object rawValue = arguments != null ? arguments.get(paramName) : null;

            // Fallback hỗ trợ tên tham số tương đương (ví dụ query / message cho tìm kiếm)
            if (rawValue == null && arguments != null && "query".equalsIgnoreCase(paramName) && arguments.containsKey("message")) {
                rawValue = arguments.get("message");
            }

            // Tự động kiểm tra tham số bắt buộc từ @McpToolParam
            if (isRequired && (rawValue == null || (rawValue instanceof String str && str.trim().isEmpty()))) {
                String desc = (paramAnno != null && !paramAnno.description().isEmpty())
                        ? " (" + paramAnno.description() + ")"
                        : "";
                throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST,
                        "Thiếu tham số bắt buộc '" + paramName + "'" + desc + " cho công cụ " + method.getName());
            }

            if (rawValue != null) {
                try {
                    resolvedArgs[i] = objectMapper.convertValue(rawValue, param.getType());
                } catch (Exception ex) {
                    throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST,
                            "Tham số '" + paramName + "' không hợp lệ: " + ex.getMessage());
                }
            } else {
                resolvedArgs[i] = getDefaultValueForType(param.getType());
            }
        }

        method.setAccessible(true);
        try {
            return method.invoke(targetBean, resolvedArgs);
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            if (cause instanceof BusinessException be) {
                throw be;
            }
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR,
                    cause != null ? cause.getMessage() : ite.getMessage());
        } catch (IllegalAccessException e) {
            throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR,
                    "Không thể truy cập phương thức công cụ: " + e.getMessage());
        }
    }

    private Object getDefaultValueForType(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(type)) {
            return false;
        }
        if (byte.class.equals(type)) {
            return (byte) 0;
        }
        if (short.class.equals(type)) {
            return (short) 0;
        }
        if (int.class.equals(type)) {
            return 0;
        }
        if (long.class.equals(type)) {
            return 0L;
        }
        if (float.class.equals(type)) {
            return 0.0f;
        }
        if (double.class.equals(type)) {
            return 0.0d;
        }
        if (char.class.equals(type)) {
            return '\0';
        }
        return null;
    }

    /**
     * Đăng ký một công cụ xử lý thủ công vào danh mục thực thi (hỗ trợ mở rộng runtime / test mock).
     *
     * @param toolName Tên công cụ (không phân biệt hoa thường)
     * @param handler Hàm xử lý nhận tham số và trả về kết quả
     */
    public void register(String toolName, Function<Map<String, Object>, Object> handler) {
        if (toolName != null && handler != null) {
            handlers.put(toolName.toLowerCase(Locale.ROOT), handler);
        }
    }

    @Override
    public Object executeTool(String toolName, Map<String, Object> arguments) {
        if (toolName == null || toolName.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Tên công cụ không được để trống.");
        }
        Function<Map<String, Object>, Object> handler = handlers.get(toolName.toLowerCase(Locale.ROOT));
        if (handler == null) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Công cụ '" + toolName + "' không được hỗ trợ.");
        }
        return handler.apply(arguments != null ? arguments : Map.of());
    }
}
