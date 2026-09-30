package com.bi.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 新建/编辑维度请求
 */
@Data
public class DimensionReq {

    private Long id;

    /**
     * 中文名称
     */
    @NotBlank(message = "维度名称不能为空")
    private String chineseName;

    /**
     * 别名
     */
    private String alias;

    /**
     * 英文名称
     */
    @NotBlank(message = "维度编码不能为空")
    private String englishName;

    @JsonSetter
    public void setEnglishName(String englishName) {
        this.englishName = englishName == null ? null : englishName.toLowerCase();
    }

    /**
     * 维度扩展信息（和返回报文结构一致）
     */
    @Valid
    @NotNull(message = "扩展信息不能为空")
    private DimensionExtensionReq extension;
}
