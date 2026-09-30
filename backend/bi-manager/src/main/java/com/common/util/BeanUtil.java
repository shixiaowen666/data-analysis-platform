package com.common.util;

import org.springframework.beans.BeanUtils;

/**
 * Bean 拷贝工具类
 */
public class BeanUtil{

    private BeanUtil() {}

    /**
     * 浅拷贝
     */
    public static void copy(Object source, Object target) {
        BeanUtils.copyProperties(source, target);
    }
}
