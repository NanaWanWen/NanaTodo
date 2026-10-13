package com.yonerp.po.common;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yonerp.po.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充：create_by / create_time / update_by / update_time
 *
 * <p>操作人取自 Redis 会话解析出的当前用户，业务代码无需关心。</p>
 */
@Slf4j
@Component
public class AuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        Long userId = currentUserId();
        fillIfNull(metaObject, "createTime", now);
        fillIfNull(metaObject, "updateTime", now);
        if (userId != null) {
            fillIfNull(metaObject, "createBy", userId);
            fillIfNull(metaObject, "updateBy", userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        Long userId = currentUserId();
        if (userId != null) {
            this.strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    private Long currentUserId() {
        try {
            return UserContext.get() == null ? null : UserContext.get().getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private void fillIfNull(MetaObject metaObject, String field, Object value) {
        if (metaObject.hasGetter(field) && metaObject.getValue(field) == null) {
            metaObject.setValue(field, value);
        }
    }
}
