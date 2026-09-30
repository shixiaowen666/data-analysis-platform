package com.chatbi.chat.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.chatbi.chat.models.SaasUser;
import com.chatbi.chat.utils.UserThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 自动填充 createTime / updateTime / createBy / updateBy
 */
@Slf4j
@Component
public class AutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        this.strictInsertFill(metaObject, "createBy", this::getCurrentUser, String.class);
        this.strictInsertFill(metaObject, "updateBy", this::getCurrentUser, String.class);
        this.strictInsertFill(metaObject, "createdBy", this::getCurrentUserId, Long.class);
        this.strictInsertFill(metaObject, "updatedBy", this::getCurrentUserId, Long.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updateBy", this::getCurrentUser, String.class);
        this.strictUpdateFill(metaObject, "updatedBy", this::getCurrentUserId, Long.class);
    }

    private String getCurrentUser() {
        SaasUser user = UserThreadLocal.get();
        return user != null ? user.getUsername() : "system";
    }

    private Long getCurrentUserId() {
        SaasUser user = UserThreadLocal.get();
        return user != null ? user.getId() : null;
    }
}
