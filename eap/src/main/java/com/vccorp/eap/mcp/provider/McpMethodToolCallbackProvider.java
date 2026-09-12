package com.vccorp.eap.mcp.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.mcp.annotation.McpTool;
import com.vccorp.eap.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quét các Bean chứa các phương thức được đánh dấu @McpTool và @McpToolParam,
 * tự động trích xuất JSON Schema chuẩn MCP và đăng ký thành ToolCallbackProvider cho Spring AI MCP Server.
 */
public class McpMethodToolCallbackProvider implements ToolCallbackProvider {

    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private final List<Object> toolObjects;
    private final ObjectMapper objectMapper;

    public McpMethodToolCallbackProvider(List<Object> toolObjects) {
        this(toolObjects, new ObjectMapper());
    }

    public McpMethodToolCallbackProvider(List<Object> toolObjects, ObjectMapper objectMapper) {
        this.toolObjects = toolObjects;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        List<ToolCallback> callbacks = new ArrayList<>();

        for (Object toolObject : toolObjects) {
            Class<?> clazz = toolObject.getClass();
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.isAnnotationPresent(McpTool.class)) {
                    McpTool mcpTool = method.getAnnotation(McpTool.class);
                    String name = (mcpTool.name() != null && !mcpTool.name().trim().isEmpty())
                            ? mcpTool.name().trim()
                            : method.getName();
                    String description = mcpTool.description();
                    String inputSchemaJson = generateInputSchema(method);

                    ToolDefinition toolDefinition = ToolDefinition.builder()
                            .name(name)
                            .description(description)
                            .inputSchema(inputSchemaJson)
                            .build();

                    MethodToolCallback callback = MethodToolCallback.builder()
                            .toolDefinition(toolDefinition)
                            .toolMethod(method)
                            .toolObject(toolObject)
                            .build();

                    callbacks.add(callback);
                }
            }
        }

        return callbacks.toArray(new ToolCallback[0]);
    }

    private String generateInputSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> requiredList = new ArrayList<>();

        Parameter[] parameters = method.getParameters();
        String[] paramNames = PARAM_NAME_DISCOVERER.getParameterNames(method);

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            String paramName = (paramNames != null && i < paramNames.length && paramNames[i] != null)
                    ? paramNames[i]
                    : parameter.getName();

            McpToolParam paramAnnotation = parameter.getAnnotation(McpToolParam.class);
            String paramDesc = paramAnnotation != null ? paramAnnotation.description() : "";
            boolean isRequired = paramAnnotation == null || paramAnnotation.required();

            Map<String, Object> propDetail = new LinkedHashMap<>();
            propDetail.put("type", mapJavaTypeToJsonType(parameter.getType()));
            if (!paramDesc.isEmpty()) {
                propDetail.put("description", paramDesc);
            }

            properties.put(paramName, propDetail);
            if (isRequired) {
                requiredList.add(paramName);
            }
        }

        schema.put("properties", properties);
        schema.put("required", requiredList);

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
        }
    }

    private String mapJavaTypeToJsonType(Class<?> type) {
        if (String.class.isAssignableFrom(type) || Character.class.isAssignableFrom(type) || char.class.equals(type)) {
            return "string";
        } else if (Integer.class.isAssignableFrom(type) || int.class.equals(type) ||
                   Long.class.isAssignableFrom(type) || long.class.equals(type) ||
                   Short.class.isAssignableFrom(type) || short.class.equals(type) ||
                   Byte.class.isAssignableFrom(type) || byte.class.equals(type)) {
            return "integer";
        } else if (Double.class.isAssignableFrom(type) || double.class.equals(type) ||
                   Float.class.isAssignableFrom(type) || float.class.equals(type)) {
            return "number";
        } else if (Boolean.class.isAssignableFrom(type) || boolean.class.equals(type)) {
            return "boolean";
        } else if (type.isArray() || Iterable.class.isAssignableFrom(type)) {
            return "array";
        }
        return "object";
    }

    public static class Builder {
        private final List<Object> toolObjects = new ArrayList<>();
        private ObjectMapper objectMapper;

        public Builder toolObjects(Object... objects) {
            if (objects != null) {
                this.toolObjects.addAll(Arrays.asList(objects));
            }
            return this;
        }

        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        public McpMethodToolCallbackProvider build() {
            return new McpMethodToolCallbackProvider(toolObjects, objectMapper);
        }
    }
}
