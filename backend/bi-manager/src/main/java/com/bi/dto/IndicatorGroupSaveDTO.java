package com.bi.dto;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Data
public class IndicatorGroupSaveDTO {

    /**
     * 组合 ID；为空新建，非空编辑
     */
    private Long id;

    @NotBlank(message = "组合编码不能为空")
    private String groupCode;

    @NotBlank(message = "组合名称不能为空")
    private String groupName;

    private String subjectDomain;

    private String description;

    /**
     * 兼容前端传 status：0-草稿 1-审批中
     */
    private Integer status;

    private String saveAction;

    @NotEmpty(message = "结构配置项不能为空")
    @Valid
    private List<GroupItemSaveDTO> items;
}
