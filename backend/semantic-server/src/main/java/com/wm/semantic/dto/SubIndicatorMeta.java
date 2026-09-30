package com.wm.semantic.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 计算指标的子指标定义，来自 formula JSON 的 indicatorList。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubIndicatorMeta {

    /** 子指标 ID */
    private Long id;

    /** 公式中的符号字母 */
    private String letter;

    /** 子指标展示名称 */
    private String name;
}
