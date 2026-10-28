package com.yonerp.po.agent.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * null 安全的工具执行器包装。
 * <p>
 * 背景：通义千问调用带可选参数的工具时会显式传 null（如 "supplierId": null），
 * langchain4j 0.36.2 的 DefaultToolExecutor.coerceArgument 对显式 null 做
 * 类型转换时会抛出 NPE（Cannot invoke "Object.getClass()" because "argument" is null），
 * 导致整次对话失败降级。
 * </p>
 * <p>
 * 方案：执行工具前把参数 JSON 中的显式 null 字段剔除（等价于"未传该参数"），
 * 再交给 DefaultToolExecutor。工具的 Java 方法内部本来就对 null 参数做了容错。
 * </p>
 */
@Slf4j
public final class NullSafeTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private NullSafeTools() {
    }

    /**
     * 构建「工具规格 → null 安全执行器」映射，供 AiServices.tools(Map) 使用
     */
    public static Map<ToolSpecification, ToolExecutor> build(List<Object> tools) {
        Map<ToolSpecification, ToolExecutor> result = new HashMap<>();
        for (Object tool : tools) {
            for (ToolSpecification spec : ToolSpecifications.toolSpecificationsFrom(tool)) {
                Method method = findAnnotatedMethod(tool, spec.name());
                ToolExecutor delegate = new DefaultToolExecutor(tool, method);
                result.put(spec, (request, memoryId) -> delegate.execute(stripNullArguments(request), memoryId));
            }
        }
        return result;
    }

    /**
     * 剔除工具调用参数 JSON 中的显式 null 字段
     */
    private static ToolExecutionRequest stripNullArguments(ToolExecutionRequest request) {
        String args = request.arguments();
        if (args == null || args.isBlank()) {
            return request;
        }
        try {
            JsonNode node = MAPPER.readTree(args);
            if (node instanceof ObjectNode obj) {
                boolean changed = false;
                Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
                while (fields.hasNext()) {
                    if (fields.next().getValue().isNull()) {
                        fields.remove();
                        changed = true;
                    }
                }
                if (changed) {
                    log.debug("Stripped null tool arguments for [{}]: {}", request.name(), args);
                    return ToolExecutionRequest.builder()
                            .id(request.id())
                            .name(request.name())
                            .arguments(MAPPER.writeValueAsString(obj))
                            .build();
                }
            }
        } catch (Exception e) {
            // 解析失败时保留原始参数，交由底层自行处理
            log.warn("Failed to sanitize tool arguments for [{}], using original: {}", request.name(), args, e);
        }
        return request;
    }

    /**
     * 按工具名（默认为方法名）找到 @Tool 注解的方法
     */
    private static Method findAnnotatedMethod(Object tool, String toolName) {
        for (Class<?> type = tool.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(toolName) && method.isAnnotationPresent(dev.langchain4j.agent.tool.Tool.class)) {
                    return method;
                }
            }
        }
        throw new IllegalArgumentException("No @Tool method named '" + toolName + "' found on " + tool.getClass());
    }
}
