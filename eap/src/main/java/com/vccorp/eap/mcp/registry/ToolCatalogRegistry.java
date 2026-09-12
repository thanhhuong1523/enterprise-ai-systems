package com.vccorp.eap.mcp.registry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.mcp.annotation.McpTool;
import com.vccorp.eap.mcp.tools.McpToolFacade;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quản lý danh mục công cụ tập trung: nạp nhãn giao diện trực tiếp từ @McpTool và metadata từ ToolCallbackProvider.
 * Hoàn toàn không phụ thuộc vào file JSON cấu hình rời rạc (Single Source of Truth).
 */
@Component
public class ToolCatalogRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolCatalogRegistry.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    private final ToolCallbackProvider toolCallbackProvider;
    private final List<McpToolFacade> toolFacades;
    private final ObjectMapper objectMapper;
    private final Map<String, ToolInfo> toolsMap = new LinkedHashMap<>();

    @Autowired
    public ToolCatalogRegistry(ToolCallbackProvider toolCallbackProvider,
                               @Autowired(required = false) List<McpToolFacade> toolFacades,
                               ObjectMapper objectMapper) {
        this.toolCallbackProvider = toolCallbackProvider;
        this.toolFacades = toolFacades != null ? toolFacades : List.of();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    public ToolCatalogRegistry(ToolCallbackProvider toolCallbackProvider, ObjectMapper objectMapper) {
        this(toolCallbackProvider, List.of(), objectMapper);
    }

    @PostConstruct
    public void init() {
        Map<String, McpTool> annotationMap = scanToolAnnotations();

        org.springframework.ai.model.function.FunctionCallback[] callbacks = toolCallbackProvider.getToolCallbacks();
        if (callbacks != null) {
            for (org.springframework.ai.model.function.FunctionCallback callback : callbacks) {
                String name = callback.getName();
                String description = callback.getDescription();
                String inputSchema = callback.getInputTypeSchema();

                McpTool mcpTool = annotationMap.get(name.toLowerCase());
                String startLabel = (mcpTool != null && mcpTool.startLabel() != null && !mcpTool.startLabel().trim().isEmpty())
                        ? mcpTool.startLabel().trim()
                        : "Đang thực hiện công cụ " + name + "...";
                String endLabel = (mcpTool != null && mcpTool.endLabel() != null && !mcpTool.endLabel().trim().isEmpty())
                        ? mcpTool.endLabel().trim()
                        : "Hoàn tất thao tác " + name;

                ToolInfo info = new ToolInfo(name, description, inputSchema, startLabel, endLabel);
                toolsMap.put(name.toLowerCase(), info);
                log.info("[ToolCatalogRegistry] Đã nạp thành công tool: '{}' (startLabel='{}')", name, startLabel);
            }
        }
    }

    private Map<String, McpTool> scanToolAnnotations() {
        Map<String, McpTool> map = new LinkedHashMap<>();
        for (McpToolFacade facade : toolFacades) {
            if (facade == null) {
                continue;
            }
            for (Method method : facade.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(McpTool.class)) {
                    McpTool mcpTool = method.getAnnotation(McpTool.class);
                    String name = (mcpTool.name() != null && !mcpTool.name().trim().isEmpty())
                            ? mcpTool.name().trim()
                            : method.getName();
                    map.put(name.toLowerCase(), mcpTool);
                }
            }
        }
        return map;
    }

    public Optional<ToolInfo> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolsMap.get(name.trim().toLowerCase()));
    }

    public List<ToolInfo> getAllTools() {
        return new ArrayList<>(toolsMap.values());
    }

    /**
     * Tự động sinh danh sách mô tả công cụ cùng schema chi tiết để nhúng vào System Prompt cho LLM.
     */
    public String buildToolListForPrompt() {
        StringBuilder sb = new StringBuilder();
        int index = 1;

        for (ToolInfo tool : toolsMap.values()) {
            sb.append(index).append(". `").append(tool.name()).append("`: ").append(tool.description()).append("\n");
            sb.append("   Input schema: ").append(formatInputSchemaForPrompt(tool.inputSchema())).append("\n");
            index++;
        }

        return sb.toString().trim();
    }

    private String formatInputSchemaForPrompt(String rawSchemaJson) {
        if (rawSchemaJson == null || rawSchemaJson.trim().isEmpty()) {
            return "{}";
        }

        try {
            JsonNode root = objectMapper.readTree(rawSchemaJson);
            JsonNode propertiesNode = root.get("properties");
            if (propertiesNode == null || !propertiesNode.isObject() || propertiesNode.isEmpty()) {
                return "{}";
            }

            Set<String> requiredFields = new HashSet<>();
            JsonNode requiredNode = root.get("required");
            if (requiredNode != null && requiredNode.isArray()) {
                for (JsonNode reqItem : requiredNode) {
                    requiredFields.add(reqItem.asText());
                }
            }

            Map<String, String> formattedProps = new LinkedHashMap<>();
            propertiesNode.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode propDetail = entry.getValue();
                String desc = propDetail.has("description") ? propDetail.get("description").asText() : "";
                boolean isRequired = requiredFields.contains(fieldName);
                String tag = isRequired ? " (bắt buộc)" : " (tùy chọn)";
                formattedProps.put(fieldName, desc.isEmpty() ? tag.trim() : desc + tag);
            });

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(formattedProps);
        } catch (Exception e) {
            return rawSchemaJson;
        }
    }

    /**
     * Render nhãn động: thay thế các placeholder {{paramName}} bằng giá trị từ input map.
     */
    public String renderLabel(String template, Map<String, Object> args) {
        if (template == null) {
            return "";
        }
        if (args == null || args.isEmpty() || !template.contains("{{")) {
            return template;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object val = args.get(key);
            String replacement = val != null ? Matcher.quoteReplacement(val.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
