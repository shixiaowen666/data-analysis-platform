package com.bi.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 杂项维值映射条目
 */
@Data
public class ValueEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 表内值
     */
    private String rawValue;

    /**
     * 展示值
     */
    private String displayValue;
}
