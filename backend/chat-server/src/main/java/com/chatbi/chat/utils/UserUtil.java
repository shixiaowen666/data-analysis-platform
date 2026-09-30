package com.chatbi.chat.utils;

import com.chatbi.chat.models.SaasUser;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 用户信息service
 **/
@Component
public class UserUtil {

    /**
     * 获取用户id
     *
     * @return
     */
    public Long getUserId() {
        /*HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        if (request == null) {
            return null;
        }
        Object userIdObj = request.getAttribute(Constant.USER_ID);

        if (userIdObj == null) {
            return null;
        }*/
        SaasUser saasUser = UserThreadLocal.get();
        return NumberUtils.toLong(saasUser.getId().toString());
    }

    /**
     * 获取用户邮箱前缀
     *
     * @return
     */
    public String getUserAccount() {
        SaasUser loginUser = getUser();
        if (loginUser != null) {
            return loginUser.getAccount();
        } else {
            return "";
        }
    }

    public SaasUser getUser() {
        if (Objects.isNull(getUserId())) {
            return null;
        }
        Integer userId = getUserId().intValue();
//        return consoleUserService.getByOauthId(userId);
        return null;
    }

    /**
     * 查询用户信息
     *
     * @param userId
     * @return
     */
    public SaasUser getUserById(Long userId) {
        if (Objects.isNull(userId)) {
            return null;
        }
//        return consoleUserService.getByOauthId(userId.intValue());
        return null;
    }

    /**
     * 获取用户姓名
     *
     * @return
     */
    public String getUserName() {
        SaasUser loginUser = getUser();
        if (loginUser != null) {
            return loginUser.getName();
        } else {
            return "";
        }

    }

    /**
     * 获取用户邮箱
     *
     * @return
     */
    public String getUserEmail() {
        SaasUser loginUser = getUser();
        if (loginUser != null) {
            return loginUser.getEmail();
        } else {
            return "";
        }

    }
}
