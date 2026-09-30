package com.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.common.base.LoginUser;
import com.common.base.UserThreadLocal;
import com.common.models.SaasUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;

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
        this.strictInsertFill(metaObject, "createdBy", this::getCurrentUser, String.class);
        this.strictInsertFill(metaObject, "updatedBy", this::getCurrentUser, String.class);
        this.strictInsertFill(metaObject, "createdBy", this::getCurrentUserId, Long.class);
        this.strictInsertFill(metaObject, "updatedBy", this::getCurrentUserId, Long.class);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime::now, LocalDateTime.class);
        this.strictUpdateFill(metaObject, "updatedBy", this::getCurrentUser, String.class);
        this.strictUpdateFill(metaObject, "updatedBy", this::getCurrentUserId, Long.class);
    }

    private String getCurrentUser() {
        SaasUser user = UserThreadLocal.get();
        if (user != null && StringUtils.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        return "system";
    }

    private Long getCurrentUserId() {
        return 0L;
    }
}
