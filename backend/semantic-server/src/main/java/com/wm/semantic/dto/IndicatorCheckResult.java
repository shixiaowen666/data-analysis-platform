package com.wm.semantic.dto;

import lombok.Data;
import java.util.Map;
import java.util.Set;

@Data
public class IndicatorCheckResult {
    private boolean covered;
    private Set<Long> allIndicators;
    private Map<String, Set<Long>> indicatorMap;
}