package com.yonerp.po.common;

/**
 * Redis Key 统一定义（规范：po:{域}:{对象}:{标识}）
 * 详细设计见 docs/04-redis-design.md
 */
public final class RedisKeys {

    private RedisKeys() {
    }

    /** 根前缀 */
    public static final String ROOT = "po:";

    /** 会话 */
    public static final String SESSION = ROOT + "session:";
    /** 基础数据与字典缓存 */
    public static final String CACHE = ROOT + "cache:";
    /** 报表缓存 */
    public static final String REPORT = ROOT + "report:";
    /** 待办角标 */
    public static final String BADGE = ROOT + "badge:";
    /** 单号序列 */
    public static final String SEQ = ROOT + "seq:";
    /** 幂等 */
    public static final String IDEM = ROOT + "idem:";
    /** 锁 */
    public static final String LOCK = ROOT + "lock:";
    /** 限流 */
    public static final String RATE = ROOT + "rate:";

    // ---------------- 会话 ----------------
    public static String session(Long userId) {
        return SESSION + userId;
    }

    public static String sessionRoles(Long userId) {
        return SESSION + userId + ":roles";
    }

    // ---------------- 缓存 ----------------
    public static String cacheUserList() {
        return CACHE + "user:list";
    }

    public static String cacheSupplier(Long id) {
        return CACHE + "supplier:" + id;
    }

    public static String cacheSupplierList() {
        return CACHE + "supplier:list";
    }

    public static String cacheMaterial(Long id) {
        return CACHE + "material:" + id;
    }

    public static String cacheMaterialList() {
        return CACHE + "material:list";
    }

    public static String cacheDict(String type) {
        return CACHE + "dict:" + type;
    }

    // ---------------- 报表 ----------------
    public static String reportSummary(String scope, String hash) {
        return REPORT + "summary:" + scope + ":" + hash;
    }

    public static String reportStatus(String scope, String hash) {
        return REPORT + "status:" + scope + ":" + hash;
    }

    public static String reportTime(String granularity, String hash) {
        return REPORT + "time:" + granularity + ":" + hash;
    }

    public static String reportRank(String groupBy, String scope, String hash) {
        return REPORT + "rank:" + groupBy + ":" + scope + ":" + hash;
    }

    // ---------------- 角标 ----------------
    public static String badgeTodo(String roleCode) {
        return BADGE + "todo:" + roleCode;
    }

    // ---------------- 序列 ----------------
    public static String seqOrder(String date) {
        return SEQ + "order:" + date;
    }

    /**
     * 通用单号序列（收货 RCPT / 发票 INV / 付款 PAY）
     */
    public static String sequence(String prefix, String date) {
        return SEQ + prefix.toLowerCase() + ":" + date;
    }

    // ---------------- 幂等 ----------------
    public static String idem(String key) {
        return IDEM + key;
    }

    // ---------------- 锁 ----------------
    public static String lockAction(Long orderId, String action) {
        return LOCK + "action:" + orderId + ":" + action;
    }

    public static String lockApprove(Long taskId) {
        return LOCK + "approve:" + taskId;
    }

    // ---------------- 限流 ----------------
    public static String rate(Long userId, String apiCode) {
        return RATE + userId + ":" + apiCode;
    }

    // ---------------- 超时提醒 ----------------
    public static String timeoutZset() {
        return ROOT + "timeout:zset";
    }

    // ---------------- AI Agent 预警 ----------------
    public static final String ALERT = ROOT + "alert:";

    public static String alertList() {
        return ALERT + "list";
    }

    public static String alertItem(String alertId) {
        return ALERT + "item:" + alertId;
    }
}
