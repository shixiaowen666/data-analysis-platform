package com.chatbi.chat.enums;

/**
 * 缓存时间时长
 *
 * @Author: gaowenqing
 * @Date: 2020/1/10
 */
public enum CacheLevelEnum {

    /**
     * 没有缓存
     */
    LEVEL_ZERO(0),
    /**
     * 缓存1天
     */
    LEVEL_ONE(1),
    /**
     * 缓存1小时
     */
    LEVEL_TWO(2),
    /**
     * 缓存1分
     */
    LEVEL_THREE(3),
    /**
     * 缓存1秒
     */
    LEVEL_FOUR(4);

    /**
     * 标识key
     */
    private Integer key;

    CacheLevelEnum(Integer level) {
        this.key = level;
    }

    public Integer getKey() {
        return key;
    }

}
