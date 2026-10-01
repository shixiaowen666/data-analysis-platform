package com.quality.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;

/**
 * 实体 → JSONObject 的统一转换。
 * 直接使用 JSON.toJSON() 会把 LocalDateTime 序列化为毫秒时间戳，
 * 这里统一走 "yyyy-MM-dd HH:mm:ss" 字符串，和全局 jackson 配置保持一致。
 */
public final class Jsons {
    private Jsons() {}

    public static JSONObject obj(Object bean) {
        if (bean == null) return null;
        if (bean instanceof JSONObject) return (JSONObject) bean;
        return JSON.parseObject(JSON.toJSONStringWithDateFormat(bean, "yyyy-MM-dd HH:mm:ss",
                SerializerFeature.WriteMapNullValue));
    }
}
