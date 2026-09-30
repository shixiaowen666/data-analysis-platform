package com.bi.util;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 计算/派生指标公式 JSON 解析工具，提取引用原子指标 ID。
 */
public final class MetricFormulaParser {

    private MetricFormulaParser() {
    }

    /**
     * 从计算指标 JSON 中提取引用的原子指标 ID。
     * JSON 格式：{ "indicatorList": [{"id": 374, ...}, ...] }
     */
    public static List<Long> extractCalcReferencedIds(String calculatedProduction) {
        if (StrUtil.isBlank(calculatedProduction)) {
            return Collections.emptyList();
        }
        try {
            JSONObject json = JSONObject.parseObject(calculatedProduction);
            JSONArray indicatorList = json.getJSONArray("indicatorList");
            if (indicatorList == null || indicatorList.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < indicatorList.size(); i++) {
                JSONObject item = indicatorList.getJSONObject(i);
                Long id = item.getLong("id");
                if (id != null) {
                    ids.add(id);
                }
            }
            return ids;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * 从派生指标 JSON 中提取引用的原子指标 ID。
     * JSON 格式：{ "indicatorId": 75, ... }
     */
    public static Long extractDeriveReferencedId(String derivativeProduction) {
        if (StrUtil.isBlank(derivativeProduction)) {
            return null;
        }
        try {
            JSONObject json = JSONObject.parseObject(derivativeProduction);
            return json.getLong("indicatorId");
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isCalcMetric(String calculatedProduction) {
        return StrUtil.isNotBlank(calculatedProduction);
    }

    public static boolean isDeriveMetric(String derivativeProduction) {
        return StrUtil.isNotBlank(derivativeProduction);
    }

    public static boolean allReferencedInSet(List<Long> referencedIds, Set<Long> availableMetricIds) {
        if (referencedIds.isEmpty()) {
            return true;
        }
        return availableMetricIds.containsAll(referencedIds);
    }
}
