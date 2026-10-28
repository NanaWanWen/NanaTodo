package com.yonerp.po.agent.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yonerp.po.agent.ai.PurchaseAgent;
import com.yonerp.po.agent.config.AgentProperties;
import com.yonerp.po.agent.dto.AgentAlertVO;
import com.yonerp.po.agent.dto.AlertActionVO;
import com.yonerp.po.agent.dto.ChatRequest;
import com.yonerp.po.agent.dto.SuggestionVO;
import com.yonerp.po.agent.entity.AgentAlert;
import com.yonerp.po.agent.entity.AgentAlertAction;
import com.yonerp.po.agent.entity.AgentAlertRule;
import com.yonerp.po.agent.mapper.AgentAlertActionMapper;
import com.yonerp.po.agent.mapper.AgentAlertMapper;
import com.yonerp.po.agent.mapper.AgentAlertRuleMapper;
import com.yonerp.po.agent.service.AgentService;
import com.yonerp.po.agent.service.AlertNotifyService;
import com.yonerp.po.agent.tool.InventoryTool;
import com.yonerp.po.agent.tool.OrderTool;
import com.yonerp.po.agent.tool.ReportTool;
import com.yonerp.po.agent.tool.SettlementTool;
import com.yonerp.po.security.CurrentUser;
import com.yonerp.po.base.entity.SysUser;
import com.yonerp.po.base.mapper.SysUserMapper;
import com.yonerp.po.common.BizException;
import com.yonerp.po.security.UserContext;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * AI Agent 服务实现
 * <p>
 * 双模式运行：
 * 1. LLM 模式：配置了 API Key（DASHSCOPE_API_KEY / ZHIPU_API_KEY）时，通过 LangChain4j 调用通义千问/智谱；
 * 2. 规则降级模式：无 API Key 或 LLM 调用失败时，自动降级为直接调用业务工具（Tool），保证 Agent 功能始终可用。
 * 用户后续配置好 API Key 重启即自动切换为 LLM 模式，无需改动代码。
 * </p>
 */
@Slf4j
@Service
public class AgentServiceImpl implements AgentService {

    private final AgentProperties agentProperties;
    @org.springframework.lang.Nullable
    private final ChatLanguageModel chatLanguageModel; // 可为 null（未配置 API Key 时）
    /** 真 AI Agent：大模型自主编排业务工具；可为 null（未配置 API Key 时） */
    @org.springframework.lang.Nullable
    private final PurchaseAgent purchaseAgent;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ReportTool reportTool;
    private final OrderTool orderTool;
    private final InventoryTool inventoryTool;
    private final SettlementTool settlementTool;
    private final AgentAlertMapper agentAlertMapper;
    private final AgentAlertRuleMapper agentAlertRuleMapper;
    private final AgentAlertActionMapper agentAlertActionMapper;
    private final SysUserMapper sysUserMapper;
    private final AlertNotifyService alertNotifyService;

    public AgentServiceImpl(AgentProperties agentProperties,
                            @org.springframework.lang.Nullable ChatLanguageModel chatLanguageModel,
                            @org.springframework.lang.Nullable PurchaseAgent purchaseAgent,
                            ReportTool reportTool,
                            OrderTool orderTool,
                            InventoryTool inventoryTool,
                            SettlementTool settlementTool,
                            AgentAlertMapper agentAlertMapper,
                            AgentAlertRuleMapper agentAlertRuleMapper,
                            AgentAlertActionMapper agentAlertActionMapper,
                            SysUserMapper sysUserMapper,
                            AlertNotifyService alertNotifyService) {
        this.agentProperties = agentProperties;
        this.chatLanguageModel = chatLanguageModel;
        this.purchaseAgent = purchaseAgent;
        this.reportTool = reportTool;
        this.orderTool = orderTool;
        this.inventoryTool = inventoryTool;
        this.settlementTool = settlementTool;
        this.agentAlertMapper = agentAlertMapper;
        this.agentAlertRuleMapper = agentAlertRuleMapper;
        this.agentAlertActionMapper = agentAlertActionMapper;
        this.sysUserMapper = sysUserMapper;
        this.alertNotifyService = alertNotifyService;
    }

    /**
     * 校验指派/转办对象必须是系统中存在且启用的真实用户（避免自由文本随便填“赵总”这类不存在的人）
     */
    private SysUser requireActiveUser(Long assigneeId) {
        if (assigneeId == null) {
            throw new BizException(4001, "请选择有效的责任人（系统用户）");
        }
        SysUser user = sysUserMapper.selectById(assigneeId);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())
                || (user.getStatus() != null && user.getStatus() == 0)) {
            throw new BizException(4001, "责任人不存在或已停用，请从用户列表中选择");
        }
        return user;
    }

    // ==================== 对话 ====================

    @Override
    public void chatStream(ChatRequest request,
                           Consumer<String> onToken,
                           BiConsumer<String, Throwable> onDone,
                           Consumer<Throwable> onError) {
        if (!agentProperties.isAvailable()) {
            // 降级模式：直接调用工具并返回 JSON
            log.info("Agent in fallback mode (no API Key): message='{}'", request.getMessage());
            String fallback = ruleBasedChat(request.getMessage());
            onToken.accept(fallback);
            onDone.accept(fallback, null);
            return;
        }

        // LLM 模式：同步调用后分块推送（简化实现，后续可升级为真正的 token 流式）
        try {
            String response = llmChat(request);
            // 模拟流式：按 10 字符分块推送
            for (int i = 0; i < response.length(); i += 10) {
                onToken.accept(response.substring(i, Math.min(i + 10, response.length())));
            }
            onDone.accept(response, null);
        } catch (Exception e) {
            String fallback = degradeWithNotice(request, e);
            onToken.accept(fallback);
            onDone.accept(fallback, e);
        }
    }

    @Override
    public String chat(ChatRequest request) {
        if (!agentProperties.isAvailable()) {
            log.info("Agent in fallback mode (no API Key): message='{}'", request.getMessage());
            return ruleBasedChat(request.getMessage());
        }
        try {
            return llmChat(request);
        } catch (Exception e) {
            return degradeWithNotice(request, e);
        }
    }

    /**
     * LLM 调用失败时降级为规则模式，并在结果中附加降级原因提示
     * <p>
     * 提示写入结果 JSON 的 note 字段（而非仅拼接文本），保证前端一定能渲染出提示文案。
     * </p>
     */
    private String degradeWithNotice(ChatRequest request, Exception e) {
        boolean capacityInsufficient = isCapacityInsufficient(e);
        String notice = capacityInsufficient
                ? "算力不足，已切换本地规则模式"
                : "AI 服务暂时不可用，已切换本地规则模式";
        log.warn("Agent LLM chat failed (capacityInsufficient={}), falling back to rule mode: {}",
                capacityInsufficient, e.getMessage());
        String fallback = ruleBasedChat(request.getMessage());
        return attachNotice(fallback, notice);
    }

    /**
     * 判定是否为算力不足：限流 / 配额耗尽 / 超时（含 DashScope Throttling 系列错误码）
     */
    private boolean isCapacityInsufficient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg == null) {
                continue;
            }
            String lower = msg.toLowerCase(Locale.ROOT);
            if (lower.contains("429")
                    || lower.contains("throttl")
                    || lower.contains("quota")
                    || lower.contains("rate limit")
                    || lower.contains("rate_limit")
                    || lower.contains("capacity")
                    || lower.contains("insufficient")
                    || lower.contains("timed out")
                    || lower.contains("timeout")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将提示附加到规则结果上：优先改写 JSON 的 note 字段，失败则前缀拼接
     */
    private String attachNotice(String json, String notice) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject() && node.has("note")) {
                ((ObjectNode) node).put("note", notice + "｜" + node.get("note").asText());
                return objectMapper.writeValueAsString(node);
            }
        } catch (Exception e) {
            log.debug("Attach notice to JSON failed, fallback to prefix: {}", e.getMessage());
        }
        return notice + "\n" + json;
    }

    /**
     * LLM 模式：由 AI Agent（AiServices）驱动，大模型**自主决策**调用业务工具取数后作答
     * <p>
     * 注意：大模型输出不稳定，可能不按约定吐 JSON 代码块。
     * 为保证前端一定能渲染表格，这里由后端兜底：若回复中缺少 data 结构，则自动追加规则取数结果。
     * </p>
     */
    private String llmChat(ChatRequest request) {
        if (purchaseAgent == null) {
            log.warn("PurchaseAgent unavailable, falling back to rule mode");
            return ruleBasedChat(request.getMessage());
        }
        log.info("Agent LLM chat: provider={}", agentProperties.getProvider());
        // 大模型自主编排 @Tool 取数并作答（today 供系统提示词推断时间范围，避免模型编造年份）
        String result = purchaseAgent.chat(request.getMessage(), LocalDate.now().toString());
        log.debug("Agent LLM response: {}", result);

        // 后端校正：LLM 可能转述错数字（抄错金额/笔数）或选错时间范围。
        // 只要规则取数能命中（含 data 字段），就以工具真实数据为准：
        // 无 JSON 块 → 追加；有 JSON 块但 data 不一致 → 整体替换
        CurrentUser currentUser = UserContext.get();
        String userName = currentUser != null ? currentUser.getRealName() : null;
        String toolData = fetchToolData(request.getMessage(), userName);
        if (toolData != null && hasDataField(toolData)) {
            result = reconcileWithToolData(result, toolData);
        }
        return result;
    }

    /** 规则取数结果是否包含 data 字段（无 data 的兜底文案不参与校正） */
    private boolean hasDataField(String toolData) {
        try {
            JsonNode node = objectMapper.readTree(toolData);
            return node.has("data") && !node.get("data").isNull();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 用工具真实数据校正 LLM 回复中的结构化数据块：
     * 回复没有 ```json 块则追加；有但与工具数据的 data 不一致则替换（防止模型抄错数字）
     */
    private String reconcileWithToolData(String result, String toolData) {
        java.util.regex.Pattern pattern =
                java.util.regex.Pattern.compile("```json\\s*\\n.*?\\n```", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(result);
        if (!matcher.find()) {
            log.info("Agent response missing structured data, appending tool data as fallback");
            // 系统结论前置：模型叙述可能含未经核对的数字，权威结论必须排在前面
            String summary = buildSummaryText(toolData);
            return (summary != null ? summary + "\n\n" : "")
                    + result.strip() + "\n\n```json\n" + toolData + "\n```";
        }
        if (dataOfJsonBlock(matcher.group()).equals(dataOf(toolData))) {
            return result; // 一致，保留模型文案
        }
        log.info("Agent response data mismatch with tool data, replacing with authoritative tool data");
        // 模型转述的数字已被证伪，结论文字也改为由工具真实数据确定性生成，确保口径全部来自系统
        String summary = buildSummaryText(toolData);
        if (summary != null) {
            return summary + "\n\n```json\n" + toolData + "\n```";
        }
        return matcher.replaceFirst(
                java.util.regex.Matcher.quoteReplacement("```json\n" + toolData + "\n```"));
    }

    /**
     * 由工具真实数据生成确定性结论文字（数字全部来自系统实时统计，杜绝模型转述错误）。
     * 覆盖全部查询类型；空数据也给固定句式，避免模型自由发挥输出病句。
     */
    private String buildSummaryText(String toolData) {
        try {
            JsonNode node = objectMapper.readTree(toolData);
            String type = node.has("type") ? node.get("type").asText() : "";
            JsonNode data = node.has("data") ? node.get("data") : null;
            if (data == null) {
                return null;
            }
            switch (type) {
                case "report_summary":
                    return buildSummaryCardText(data);
                case "order_status_summary":
                    return buildStatusText(data);
                case "order_list":
                    return buildOrderListText(data, "订单");
                case "overdue_orders":
                    return buildOrderListText(data, "逾期订单");
                case "low_stock":
                    return buildInventoryText(data, true);
                case "inventory_list":
                    return buildInventoryText(data, false);
                case "supplier_balance":
                    return buildBalanceText(data);
                case "report_rank":
                    return buildRankText(data);
                default:
                    return null;
            }
        } catch (Exception e) {
            log.warn("buildSummaryText failed: {}", e.getMessage());
        }
        return null;
    }

    private String buildSummaryCardText(JsonNode data) {
        if (!data.isObject()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("结论（数据来自系统实时统计）：");
        sb.append("共 ").append(num(data, "totalCount")).append(" 笔订单，总金额 ")
                .append(amt(data, "totalAmount")).append(" 元");
        sb.append("；已完成 ").append(num(data, "completedCount")).append(" 笔（")
                .append(amt(data, "completedAmount")).append(" 元）");
        sb.append("，已审批 ").append(num(data, "approvedCount")).append(" 笔");
        sb.append("，审批中 ").append(num(data, "approvingCount")).append(" 笔（")
                .append(amt(data, "approvingAmount")).append(" 元）");
        sb.append("，草稿 ").append(num(data, "draftCount")).append(" 笔");
        sb.append("，已驳回 ").append(num(data, "rejectedCount")).append(" 笔");
        sb.append("，已取消 ").append(num(data, "canceledCount")).append(" 笔。");
        return sb.toString();
    }

    private String buildStatusText(JsonNode data) {
        if (!data.isArray() || data.isEmpty()) {
            return "结论（数据来自系统实时统计）：当前没有符合条件的订单。";
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode row : data) {
            parts.add(statusName(text(row, "status")) + " " + num(row, "count") + " 笔（"
                    + amt(row, "amount") + " 元）");
        }
        return "结论（数据来自系统实时统计）：各状态 " + String.join("、", parts) + "。";
    }

    /** 订单列表/逾期订单：data 为分页对象 {records:[...], total}，也可能直接是数组 */
    private String buildOrderListText(JsonNode data, String label) {
        JsonNode records = data.has("records") && data.get("records").isArray()
                ? data.get("records") : data;
        long total = data.has("total") ? data.get("total").asLong() : records.size();
        if (!records.isArray() || records.isEmpty()) {
            return "结论（数据来自系统实时统计）：当前没有符合条件的" + label + "。";
        }
        BigDecimal pageAmount = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        for (JsonNode r : records) {
            pageAmount = pageAmount.add(dec(r, "totalAmount"));
        }
        int limit = Math.min(records.size(), 5);
        for (int i = 0; i < limit; i++) {
            JsonNode r = records.get(i);
            parts.add(text(r, "orderNo") + "（" + text(r, "title") + "，"
                    + dec(r, "totalAmount").toPlainString() + " 元，"
                    + statusName(text(r, "status")) + "）");
        }
        StringBuilder sb = new StringBuilder("结论（数据来自系统实时统计）：共 ").append(total)
                .append(" 笔").append(label).append("（本页 ").append(records.size())
                .append(" 笔，金额合计 ").append(pageAmount.toPlainString()).append(" 元）");
        sb.append("；明细：").append(String.join("、", parts));
        if (records.size() > limit || total > records.size()) {
            sb.append(" 等");
        }
        return sb.append("。").toString();
    }

    /** 库存列表 / 低库存预警 */
    private String buildInventoryText(JsonNode data, boolean lowOnly) {
        if (!data.isArray() || data.isEmpty()) {
            return lowOnly
                    ? "结论（数据来自系统实时统计）：当前没有低于安全库存的物料。"
                    : "结论（数据来自系统实时统计）：当前没有库存记录。";
        }
        BigDecimal qtySum = BigDecimal.ZERO;
        int lowCount = 0;
        List<String> parts = new ArrayList<>();
        for (JsonNode r : data) {
            BigDecimal qty = dec(r, "quantity");
            BigDecimal safety = dec(r, "safetyStock");
            qtySum = qtySum.add(qty);
            if (qty.compareTo(safety) < 0) {
                lowCount++;
            }
            if (parts.size() < 5) {
                parts.add(text(r, "materialName") + "（" + text(r, "materialCode") + "）现存 "
                        + qty.toPlainString() + "，安全库存 " + safety.toPlainString());
            }
        }
        StringBuilder sb = new StringBuilder("结论（数据来自系统实时统计）：");
        if (lowOnly) {
            sb.append("共 ").append(data.size()).append(" 个物料低于安全库存");
        } else {
            sb.append("共 ").append(data.size()).append(" 条库存记录，库存总量 ")
                    .append(qtySum.toPlainString()).append("，其中低于安全库存 ").append(lowCount).append(" 个");
        }
        sb.append("；明细：").append(String.join("、", parts));
        if (data.size() > parts.size()) {
            sb.append(" 等");
        }
        return sb.append("。").toString();
    }

    /** 供应商应付余额 */
    private String buildBalanceText(JsonNode data) {
        if (!data.isArray() || data.isEmpty()) {
            return "结论（数据来自系统实时统计）：当前没有供应商应付记录。";
        }
        BigDecimal balanceSum = BigDecimal.ZERO;
        List<String> parts = new ArrayList<>();
        for (JsonNode r : data) {
            balanceSum = balanceSum.add(dec(r, "balance"));
            if (parts.size() < 5) {
                parts.add(text(r, "supplierName") + "（未付 " + amt(r, "unpaidAmount") + " 元/"
                        + num(r, "unpaidInvoiceCount") + " 笔，已付 " + amt(r, "paidAmount")
                        + " 元，余额 " + amt(r, "balance") + " 元）");
            }
        }
        StringBuilder sb = new StringBuilder("结论（数据来自系统实时统计）：共 ").append(data.size())
                .append(" 家供应商存在应付记录，应付余额合计 ").append(balanceSum.toPlainString()).append(" 元");
        sb.append("；明细：").append(String.join("、", parts));
        if (data.size() > parts.size()) {
            sb.append(" 等");
        }
        return sb.append("。").toString();
    }

    /** 维度排行 */
    private String buildRankText(JsonNode data) {
        JsonNode rows = data.has("rows") && data.get("rows").isArray()
                ? data.get("rows") : data;
        if (!rows.isArray() || rows.isEmpty()) {
            return "结论（数据来自系统实时统计）：当前没有排行数据。";
        }
        String dim = switch (text(data, "groupBy")) {
            case "buyer" -> "采购员";
            case "material" -> "物料";
            case "dept" -> "部门";
            default -> "供应商";
        };
        JsonNode total = data.has("total") ? data.get("total") : null;
        StringBuilder sb = new StringBuilder("结论（数据来自系统实时统计）：按").append(dim)
                .append("采购金额排行");
        if (total != null) {
            sb.append("（合计 ").append(num(total, "orderCount")).append(" 笔订单、")
                    .append(amt(total, "amount")).append(" 元）");
        }
        List<String> parts = new ArrayList<>();
        int limit = Math.min(rows.size(), 5);
        for (int i = 0; i < limit; i++) {
            JsonNode r = rows.get(i);
            parts.add((i + 1) + ". " + text(r, "name") + " " + amt(r, "amount")
                    + " 元（占比 " + amt(r, "percent") + "%）");
        }
        sb.append("：").append(String.join("，", parts));
        if (rows.size() > limit) {
            sb.append(" 等");
        }
        return sb.append("。").toString();
    }

    private long num(JsonNode data, String field) {
        return data.has(field) && !data.get(field).isNull() ? data.get(field).asLong() : 0;
    }

    private String text(JsonNode data, String field) {
        return data.has(field) && !data.get(field).isNull() ? data.get(field).asText() : "";
    }

    private BigDecimal dec(JsonNode data, String field) {
        if (data.has(field) && !data.get(field).isNull()) {
            return data.get(field).decimalValue();
        }
        return BigDecimal.ZERO;
    }

    private String amt(JsonNode data, String field) {
        if (data.has(field) && !data.get(field).isNull()) {
            return data.get(field).decimalValue().toPlainString();
        }
        return "0.00";
    }

    /** 提取 JSON 文本中的 data 节点（解析失败返回空节点，保证比较不误判为一致） */
    private JsonNode dataOf(String jsonText) {
        try {
            String raw = jsonText.strip();
            if (raw.startsWith("```")) {
                raw = raw.replaceAll("^```json\\s*", "").replaceAll("```\\s*$", "");
            }
            JsonNode node = objectMapper.readTree(raw);
            return node.has("data") ? node.get("data") : objectMapper.createObjectNode();
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private JsonNode dataOfJsonBlock(String block) {
        return dataOf(block);
    }

    /** 判断 LLM 回复中是否已包含可解析的结构化数据 */
    private boolean containsStructuredData(String text) {
        if (text == null) return false;
        String s = text.trim();
        if (s.startsWith("{")) return true;
        return s.contains("```") && s.contains("\"data\"");
    }

    /**
     * LLM 模式：单条 UserMessage 调用
     */
    private String llmGenerate(String prompt) {
        Response<AiMessage> response = chatLanguageModel.generate(UserMessage.from(prompt));
        return response.content().text();
    }

    /**
     * 规则降级模式：根据消息关键词直接调用工具，返回 JSON 结果
     * <p>
     * 支持的自然语言特征：
     * - "我的"：note 标注当前用户，底层服务通过 UserContext 自动过滤数据
     * - "上个月"/"本月"/"去年"等：解析时间范围
     * - "各状态"/"状态分布"：返回状态统计
     * - "前N名"/"TOP N"：返回排行数据
     * </p>
     */
    private String ruleBasedChat(String message) {
        String msg = message != null ? message : "";
        log.info("Agent rule-based mode: keyword matching for '{}' ", msg);

        // 获取当前用户上下文（数据权限由底层服务通过 UserContext 自动处理）
        CurrentUser currentUser = UserContext.get();
        String userName = currentUser != null ? currentUser.getRealName() : null;

        String toolData = fetchToolData(msg, userName);
        if (toolData != null) {
            // 在 JSON 开头插入 note 说明
            return toolData.replaceFirst("\\{",
                    "{\"note\":\"AI 服务暂不可用（未配置 API Key），以下为规则查询结果\",");
        }
        return buildNoMatchResponse();
    }

    /**
     * 构建未匹配关键词时的提示响应
     */
    private String buildNoMatchResponse() {
        return "{\"note\":\"未识别的查询，请尝试以下关键词："
                + "我的订单、上个月订单、各状态分布、逾期订单、库存预警、供应商余额、采购排行。\","
                + "\"examples\":["
                + "\"上个月各状态的我的采购订单有多少？\","
                + "\"本月我的采购金额是多少？\","
                + "\"逾期订单有哪些？\","
                + "\"前5名供应商采购金额排行\","
                + "\"库存低于安全库存的物料有哪些？\"]}";
    }

    /**
     * 解析时间范围关键词
     */
    private LocalDate queryStartDate(String message, LocalDate today) {
        if (message.contains("本月") || message.contains("这个月") || message.contains("这月")
                || message.contains("当月") || message.contains("本月度")) return today.withDayOfMonth(1);
        if (message.contains("今天") || message.contains("今日") || message.contains("当天")) return today;
        if (message.contains("上月") || message.contains("上个月")) return today.minusMonths(1).withDayOfMonth(1);
        if (message.contains("去年")) return today.minusYears(1).withDayOfYear(1);
        if (message.contains("今年")) return today.withDayOfYear(1);
        if (message.contains("近半年") || message.contains("半年")) return today.minusMonths(6);
        if (message.contains("近三个月") || message.contains("三个月") || message.contains("最近三个月")) return today.minusMonths(3);
        if (message.contains("近一个月") || message.contains("一个月") || message.contains("最近一个月")) return today.minusMonths(1);
        if (message.contains("近一周") || message.contains("本周") || message.contains("最近一周")) return today.minusDays(7);
        return today.minusMonths(6);
    }

    private LocalDate queryEndDate(String message, LocalDate today) {
        if (message.contains("上月") || message.contains("上个月")) return today.withDayOfMonth(1).minusDays(1);
        if (message.contains("去年")) return today.minusYears(1).withDayOfYear(1).plusYears(1).minusDays(1);
        return today;
    }

    /**
     * 解析 TOP N 关键词
     */
    private int parseTopN(String message, int defaultN) {
        // 匹配"前N名"、"TOP N"、"N名"等模式
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("(?:前|TOP\\s*)(\\d+)(?:名)?", java.util.regex.Pattern.CASE_INSENSITIVE);
        java.util.regex.Matcher m = p.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return defaultN;
    }

    /**
     * 判断用户提问是否强调"我的"（仅用于 note 文案，数据权限由底层服务统一处理）
     */
    private boolean mentionsMyData(String message) {
        return message.contains("我的") || message.contains("我这") || message.contains("本人") || message.contains("自己");
    }

    /**
     * 拼装 note：若用户问"我的"，带上其姓名，便于回答更贴合语境
     */
    private String withUserScope(String note, String userName, boolean myData) {
        if (myData && userName != null) {
            return userName + "的" + note;
        }
        return note;
    }

    /**
     * 根据消息关键词查询真实业务数据，并返回前端可解析的结构化 JSON。
     * <p>
     * 数据权限说明：订单/报表/库存等底层服务均通过 {@link UserContext} 自动识别当前角色，
     * 采购员只能看到本人数据（管理员/审批角色看全部），因此这里无需显式传 userId。
     * </p>
     *
     * @param message  用户输入的消息
     * @param userName 当前用户姓名（仅用于 note 文案）
     */
    private String fetchToolData(String message, String userName) {
        String msg = message != null ? message : "";
        LocalDate today = LocalDate.now();
        LocalDate startDate = queryStartDate(msg, today);
        LocalDate endDate = queryEndDate(msg, today);
        boolean myData = mentionsMyData(msg);
        int topN = parseTopN(msg, 10);

        // 1. 逾期订单查询
        if (msg.contains("逾期") || msg.contains("overdue")) {
            String note = withUserScope("到货日期已过且仍在审批中的采购订单", userName, myData);
            return "{\"type\":\"overdue_orders\",\"note\":\"" + note + "\",\"data\":"
                    + orderTool.queryOverdueOrders() + "}";
        }

        // 2. 库存预警查询
        if (msg.contains("库存") && (msg.contains("预警") || msg.contains("低") || msg.contains("安全"))) {
            return "{\"type\":\"low_stock\",\"note\":\"当前库存低于安全库存的物料\",\"data\":"
                    + inventoryTool.lowStockAlerts() + "}";
        }

        // 3. 全量库存查询
        if (msg.contains("库存") && (msg.contains("有多少") || msg.contains("列表") || msg.contains("所有"))) {
            return "{\"type\":\"inventory_list\",\"note\":\"当前库存列表\",\"data\":"
                    + inventoryTool.listInventory() + "}";
        }

        // 4. 金额排行查询（优先级高于供应商余额，避免"前5名供应商采购金额排行"被误匹配）
        if (msg.contains("排行") || msg.contains("TOP") || msg.contains("前")
                || (msg.contains("金额") && msg.contains("统计"))) {
            String groupBy = "supplier";
            if (msg.contains("物料") || msg.contains("商品")) groupBy = "material";
            else if (msg.contains("采购员") || msg.contains("采购人")) groupBy = "buyer";
            else if (msg.contains("部门")) groupBy = "dept";

            String dimName = switch (groupBy) {
                case "material" -> "物料";
                case "buyer" -> "采购员";
                case "dept" -> "部门";
                default -> "供应商";
            };
            String note = withUserScope(
                    "采购金额排行（按" + dimName + "，TOP " + topN + "）", userName, myData);
            return "{\"type\":\"report_rank\",\"note\":\"" + note + "\",\"data\":"
                    + reportTool.rank(groupBy, startDate, endDate, topN) + "}";
        }

        // 5. 供应商应付余额查询
        if (msg.contains("供应商") || msg.contains("应付") || msg.contains("余额") || msg.contains("付款")) {
            return "{\"type\":\"supplier_balance\",\"note\":\"各供应商应付余额统计\",\"data\":"
                    + settlementTool.supplierBalances() + "}";
        }

        // 6. 订单/采购订单相关查询（核心场景）
        if (msg.contains("订单") || msg.contains("采购")) {
            // 6.1 各状态分布统计：优先匹配"各状态/状态分布"
            if (msg.contains("各状态") || msg.contains("状态分布")
                    || (msg.contains("状态") && (msg.contains("多少") || msg.contains("几")))) {
                String note = withUserScope(
                        "采购订单各状态分布（" + startDate + " 至 " + endDate + "）", userName, myData);
                return "{\"type\":\"order_status_summary\",\"note\":\"" + note + "\",\"data\":"
                        + reportTool.byStatus(startDate, endDate) + "}";
            }

            // 6.2 汇总统计（总金额/总数量/订单数）
            if (msg.contains("汇总") || msg.contains("总额") || msg.contains("总金额") || msg.contains("总数量")
                    || msg.contains("多少") || msg.contains("几个") || msg.contains("几笔")) {
                String note = withUserScope(
                        "采购订单汇总（" + startDate + " 至 " + endDate + "）", userName, myData);
                return "{\"type\":\"report_summary\",\"note\":\"" + note + "\",\"data\":"
                        + reportTool.summary(startDate, endDate) + "}";
            }

            // 6.3 订单列表查询（可带状态；兼容"审批中的订单有哪些"等倒装语序）
            if (msg.contains("哪些") || msg.contains("订单列表") || msg.contains("查询订单")
                    || msg.contains("列出") || msg.contains("订单明细") || msg.contains("清单")) {
                String status = parseStatus(msg);
                String note = "采购订单列表";
                if (status != null) note = statusName(status) + "状态的" + note;
                note = withUserScope(note, userName, myData);
                return "{\"type\":\"order_list\",\"note\":\"" + note + "\",\"data\":"
                        + orderTool.queryOrders(status, null, null) + "}";
            }
        }

        // 7. 兜底：报表/统计类（含"咋样/怎么样/如何/怎样/概况"等口语化总结问法）
        if (msg.contains("报表") || msg.contains("统计") || msg.contains("情况") || msg.contains("数据")
                || msg.contains("咋样") || msg.contains("怎么样") || msg.contains("怎样")
                || msg.contains("如何") || msg.contains("表现") || msg.contains("概况")
                || msg.contains("概览") || msg.contains("汇报")) {
            String note = withUserScope(
                    "采购订单汇总（" + startDate + " 至 " + endDate + "）", userName, myData);
            return "{\"type\":\"report_summary\",\"note\":\"" + note + "\",\"data\":"
                    + reportTool.summary(startDate, endDate) + "}";
        }

        return null;
    }

    /**
     * 从消息中解析订单状态（返回状态码）
     */
    private String parseStatus(String msg) {
        if (msg.contains("审批中") || msg.contains("待审批")) return "APPROVING";
        if (msg.contains("已审批") || msg.contains("已通过") || msg.contains("审批通过")) return "APPROVED";
        if (msg.contains("已驳回") || msg.contains("驳回")) return "REJECTED";
        if (msg.contains("已完成") || msg.contains("完成")) return "COMPLETED";
        if (msg.contains("草稿")) return "DRAFT";
        if (msg.contains("已取消") || msg.contains("取消")) return "CANCELED";
        return null;
    }

    /**
     * 状态码转中文名（用于 note 文案）
     */
    private String statusName(String status) {
        if (status == null) return "";
        return switch (status) {
            case "DRAFT" -> "草稿";
            case "APPROVING" -> "审批中";
            case "APPROVED" -> "已审批";
            case "REJECTED" -> "已驳回";
            case "COMPLETED" -> "已完成";
            case "CANCELED" -> "已取消";
            default -> status;
        };
    }

    // ==================== 预警 ====================

    @Override
    public AgentAlertVO generateAlert(String alertType, Long refId, String refNo, String detail) {
        AgentAlertVO alert = new AgentAlertVO();
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setAlertType(alertType);
        alert.setRefId(refId);
        alert.setRefNo(refNo);
        alert.setCreateTime(LocalDateTime.now());
        alert.setHandled(false);

        String title;
        String content;
        switch (alertType) {
            case "OVERDUE_ORDER":
                title = "逾期订单预警";
                content = detail != null ? detail : "存在期望到货日期已过的订单";
                alert.setLevel("WARNING");
                break;
            case "LOW_STOCK":
                title = "库存预警";
                content = detail != null ? detail : "库存低于安全库存";
                alert.setLevel("CRITICAL");
                break;
            case "PRICE_ABNORMAL":
                title = "价格异常预警";
                content = detail != null ? detail : "采购价格波动超过阈值";
                alert.setLevel("INFO");
                break;
            default:
                title = "系统预警";
                content = detail != null ? detail : "未知预警";
                alert.setLevel("INFO");
        }

        alert.setTitle(title);
        alert.setContent(content);

        // 根据规则始终提供可执行建议；LLM 可用时再补充说明，不让预警依赖外部接口
        String advice = switch (alertType) {
            case "OVERDUE_ORDER" -> "建议：打开关联订单，联系当前审批节点或供应商确认延期原因；更新预计到货日期并记录跟进结果。";
            case "LOW_STOCK" -> "建议：核对可用库存与未到货采购单，确认需求后创建补货订单；不要仅依据预警自动下单。";
            default -> "建议：打开关联业务单据核实数据，确认后按系统流程处理。";
        };
        if (agentProperties.isAvailable()) {
            try {
                String aiAdvice = llmGenerate(
                        "你是采购系统预警助手。根据以下事实给出不超过80字的处理建议，不得假定未提供的数据。\n"
                                + "类型：" + alertType + "\n事实：" + content
                );
                if (aiAdvice != null && !aiAdvice.isBlank()) advice = aiAdvice.strip();
            } catch (Exception e) {
                log.warn("AI alert advice failed, using rule suggestion: {}", e.getMessage());
            }
        }
        alert.setContent(content + "\n【处理建议】" + advice);

        storeAlert(alert);
        log.warn("Alert triggered: type={} refId={} refNo={} level={}", alertType, refId, refNo, alert.getLevel());
        return alert;
    }

    private void storeAlert(AgentAlertVO alert) {
        try {
            AgentAlert entity = toEntity(alert);
            agentAlertMapper.insert(entity);
            log.info("Alert stored: id={} type={}", alert.getAlertId(), alert.getAlertType());
        } catch (Exception e) {
            log.error("Failed to store alert: {}", e.getMessage());
        }
    }

    @Override
    public List<AgentAlertVO> listAlerts(Boolean handled, Boolean ignored) {
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlert> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(AgentAlert::getHandled, Boolean.TRUE.equals(handled) ? 1 : 0);
            wrapper.eq(AgentAlert::getIgnored, Boolean.TRUE.equals(ignored) ? 1 : 0);
            wrapper.orderByDesc(AgentAlert::getCreateTime);
            return agentAlertMapper.selectList(wrapper).stream().map(this::toVO).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch alerts: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<AgentAlertVO> pendingAlerts() {
        return listAlerts(false, false);
    }

    @Override
    public void markAlertHandled(String alertId) {
        updateAlert(alertId, alert -> {
            alert.setHandled(1);
            alert.setHandleTime(LocalDateTime.now());
        }, "handled");
    }

    @Override
    public void ignoreAlert(String alertId) {
        updateAlert(alertId, alert -> alert.setIgnored(1), "ignored");
    }

    @Override
    public void restoreAlert(String alertId) {
        updateAlert(alertId, alert -> alert.setIgnored(0), "restored");
    }

    private void updateAlert(String alertId, java.util.function.Consumer<AgentAlert> mutator, String action) {
        try {
            AgentAlert alert = agentAlertMapper.selectById(alertId);
            if (alert == null) return;
            mutator.accept(alert);
            agentAlertMapper.updateById(alert);
            log.info("Alert {}: id={}", action, alertId);
        } catch (Exception e) {
            log.warn("Failed to update alert ({}): {}", action, e.getMessage());
        }
    }

    private AgentAlert toEntity(AgentAlertVO vo) {
        AgentAlert entity = new AgentAlert();
        entity.setAlertId(vo.getAlertId());
        entity.setAlertType(vo.getAlertType());
        entity.setBizDomain(vo.getBizDomain());
        entity.setRuleCode(vo.getRuleCode());
        entity.setRuleId(vo.getRuleId());
        entity.setLevel(vo.getLevel());
        entity.setTitle(vo.getTitle());
        entity.setContent(vo.getContent());
        entity.setRefId(vo.getRefId());
        entity.setRefNo(vo.getRefNo());
        entity.setRefType(vo.getRefType());
        entity.setActionPath(vo.getActionPath());
        entity.setHandled(Boolean.TRUE.equals(vo.getHandled()) ? 1 : 0);
        entity.setIgnored(0);
        entity.setReadFlag(Boolean.TRUE.equals(vo.getReadFlag()) ? 1 : 0);
        entity.setAssigneeId(vo.getAssigneeId());
        entity.setAssignee(vo.getAssignee());
        entity.setCreateTime(vo.getCreateTime() != null ? vo.getCreateTime() : LocalDateTime.now());
        return entity;
    }

    private AgentAlertVO toVO(AgentAlert entity) {
        AgentAlertVO vo = new AgentAlertVO();
        vo.setAlertId(entity.getAlertId());
        vo.setAlertType(entity.getAlertType());
        vo.setBizDomain(entity.getBizDomain());
        vo.setRuleCode(entity.getRuleCode());
        vo.setRuleId(entity.getRuleId());
        vo.setLevel(entity.getLevel());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setRefId(entity.getRefId());
        vo.setRefNo(entity.getRefNo());
        vo.setRefType(entity.getRefType());
        vo.setHandled(Integer.valueOf(1).equals(entity.getHandled()));
        vo.setIgnored(Integer.valueOf(1).equals(entity.getIgnored()));
        vo.setReadFlag(Integer.valueOf(1).equals(entity.getReadFlag()));
        vo.setAssigneeId(entity.getAssigneeId());
        vo.setAssignee(entity.getAssignee());
        vo.setHandleBy(entity.getHandleBy());
        vo.setHandleTime(entity.getHandleTime());
        vo.setHandleNote(entity.getHandleNote());
        vo.setCreateTime(entity.getCreateTime());
        vo.setActionPath(entity.getActionPath());
        return vo;
    }

    // ==================== 辅助审批 / 发票校验 / 付款建议 ====================

    @Override
    public SuggestionVO suggestApproval(Long orderId, String orderContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("APPROVAL");
        vo.setRefId(orderId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工审核订单：" + orderContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是采购审批助手。请根据以下订单信息生成审批意见草稿（包含合理性判断、风险提示、建议）：\n"
                    + orderContext + "\n请用 JSON 返回，字段：suggestion（审批意见）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI approval suggestion: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI approval suggestion failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工审核订单：" + orderContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    @Override
    public SuggestionVO suggestInvoiceCheck(Long invoiceId, String invoiceContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("INVOICE_CHECK");
        vo.setRefId(invoiceId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工核对发票：" + invoiceContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是发票校验助手。请根据以下发票信息生成校验结果（金额合理性、税率合规性、与订单/收货差异）：\n"
                    + invoiceContext + "\n请用 JSON 返回，字段：suggestion（校验结果）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI invoice check: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI invoice check failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工核对发票：" + invoiceContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    @Override
    public SuggestionVO suggestPayment(Long invoiceId, String invoiceContext) {
        SuggestionVO vo = new SuggestionVO();
        vo.setType("PAYMENT");
        vo.setRefId(invoiceId);
        vo.setAiGenerated(agentProperties.isAvailable());

        if (!agentProperties.isAvailable()) {
            vo.setSuggestion("AI 服务暂不可用（未配置 API Key）。请人工决定付款：" + invoiceContext);
            vo.setRisk("MEDIUM");
            return vo;
        }

        try {
            String prompt = "你是付款建议助手。请根据以下发票信息生成付款建议（建议金额、付款方式、风险提示）：\n"
                    + invoiceContext + "\n请用 JSON 返回，字段：suggestion（付款建议）、risk（LOW/MEDIUM/HIGH）";
            String response = llmGenerate(prompt);
            log.debug("AI payment suggestion: {}", response);
            parseSuggestionJson(response, vo);
        } catch (Exception e) {
            log.warn("AI payment suggestion failed: {}", e.getMessage());
            vo.setSuggestion("AI 服务暂不可用。请人工决定付款：" + invoiceContext);
            vo.setRisk("MEDIUM");
        }
        return vo;
    }

    // ==================== 私有工具 ====================

    private void parseSuggestionJson(String response, SuggestionVO vo) {
        if (response == null || response.isBlank()) {
            vo.setSuggestion("AI 返回为空，请人工确认");
            vo.setRisk("MEDIUM");
            return;
        }
        try {
            var node = objectMapper.readTree(response);
            vo.setSuggestion(node.path("suggestion").asText(response));
            vo.setRisk(node.path("risk").asText("MEDIUM"));
        } catch (Exception e) {
            // 非 JSON 格式，原样存入
            vo.setSuggestion(response);
            vo.setRisk("MEDIUM");
        }
    }

    // ==================== 扩展：闭环处理与审计 ====================

    @Override
    public void markAlertRead(String alertId) {
        try {
            AgentAlert alert = agentAlertMapper.selectById(alertId);
            if (alert == null || alert.getReadFlag() != null && alert.getReadFlag() == 1) return;
            alert.setReadFlag(1);
            agentAlertMapper.updateById(alert);
            recordAction(alertId, "READ", null, null, null, null);
        } catch (Exception e) {
            log.warn("Failed to mark alert read: {}", alertId, e);
        }
    }

    @Override
    public void assignAlert(String alertId, Long assigneeId, String assignee, String note) {
        SysUser target = requireActiveUser(assigneeId);
        try {
            AgentAlert alert = agentAlertMapper.selectById(alertId);
            if (alert == null) {
                log.warn("指派失败：预警不存在 {}", alertId);
                return;
            }
            String from = alert.getAssignee();
            alert.setAssigneeId(target.getId());
            alert.setAssignee(target.getRealName());
            agentAlertMapper.updateById(alert);
            recordAction(alertId, "ASSIGN", from, target.getRealName(), currentOperator(), note);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to assign alert: {}", alertId, e);
        }
    }

    @Override
    public void transferAlert(String alertId, Long assigneeId, String assignee, String note) {
        SysUser target = requireActiveUser(assigneeId);
        try {
            AgentAlert alert = agentAlertMapper.selectById(alertId);
            if (alert == null) {
                log.warn("转办失败：预警不存在 {}", alertId);
                return;
            }
            String from = alert.getAssignee();
            alert.setAssigneeId(target.getId());
            alert.setAssignee(target.getRealName());
            agentAlertMapper.updateById(alert);
            recordAction(alertId, "TRANSFER", from, target.getRealName(), currentOperator(), note);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to transfer alert: {}", alertId, e);
        }
    }

    /** 当前操作人姓名（取不到登录上下文时为 null，时间线仍记录动作本身） */
    private String currentOperator() {
        CurrentUser currentUser = UserContext.get();
        return currentUser != null ? currentUser.getRealName() : null;
    }

    @Override
    public void processAlert(String alertId, String note) {
        try {
            AgentAlert alert = agentAlertMapper.selectById(alertId);
            if (alert == null) return;
            alert.setHandled(1);
            alert.setHandleTime(LocalDateTime.now());
            alert.setHandleNote(note);
            agentAlertMapper.updateById(alert);
            recordAction(alertId, "HANDLE", null, null, null, note);
        } catch (Exception e) {
            log.warn("Failed to process alert: {}", alertId, e);
        }
    }

    @Override
    public List<AlertActionVO> getAlertActions(String alertId) {
        try {
            return agentAlertActionMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlertAction>()
                            .eq(AgentAlertAction::getAlertId, alertId)
                            .orderByAsc(AgentAlertAction::getCreateTime)
            ).stream().map(this::toActionVO).toList();
        } catch (Exception e) {
            log.warn("Failed to fetch alert actions: {}", alertId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public long unreadCount() {
        try {
            return agentAlertMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentAlert>()
                            .eq(AgentAlert::getHandled, 0)
                            .eq(AgentAlert::getIgnored, 0)
                            .eq(AgentAlert::getReadFlag, 0)
            );
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public List<AgentAlertRule> listAll() {
        try {
            return agentAlertRuleMapper.selectList(null);
        } catch (Exception e) {
            log.warn("Failed to list alert rules", e);
            return Collections.emptyList();
        }
    }

    @Override
    public AgentAlertRule create(AgentAlertRule rule) {
        try {
            rule.setCreateTime(LocalDateTime.now());
            agentAlertRuleMapper.insert(rule);
            return rule;
        } catch (Exception e) {
            log.warn("Failed to create alert rule", e);
            throw e;
        }
    }

    @Override
    public AgentAlertRule update(Long id, AgentAlertRule rule) {
        try {
            rule.setId(id);
            rule.setUpdateTime(LocalDateTime.now());
            agentAlertRuleMapper.updateById(rule);
            return rule;
        } catch (Exception e) {
            log.warn("Failed to update alert rule: {}", id, e);
            throw e;
        }
    }

    @Override
    public void delete(Long id) {
        try {
            agentAlertRuleMapper.deleteById(id);
        } catch (Exception e) {
            log.warn("Failed to delete alert rule: {}", id, e);
        }
    }

    private void recordAction(String alertId, String action,
                              String fromUser, String toUser,
                              String operator, String note) {
        try {
            AgentAlertAction entity = new AgentAlertAction();
            entity.setAlertId(alertId);
            entity.setAction(action);
            entity.setFromUser(fromUser);
            entity.setToUser(toUser);
            entity.setOperator(operator);
            entity.setNote(note);
            entity.setCreateTime(LocalDateTime.now());
            agentAlertActionMapper.insert(entity);
        } catch (Exception e) {
            log.warn("Failed to record alert action: {} - {}", alertId, action, e);
        }
    }

    private AlertActionVO toActionVO(AgentAlertAction entity) {
        AlertActionVO vo = new AlertActionVO();
        vo.setId(entity.getId());
        vo.setAlertId(entity.getAlertId());
        vo.setAction(entity.getAction());
        vo.setFromUser(entity.getFromUser());
        vo.setToUser(entity.getToUser());
        vo.setOperator(entity.getOperator());
        vo.setOperatorId(entity.getOperatorId());
        vo.setNote(entity.getNote());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
