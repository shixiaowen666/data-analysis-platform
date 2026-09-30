package com.senses.permission.model.vo;

import com.senses.permission.model.param.TagParam;
import com.senses.permission.model.param.TagValueParam;
import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户标签值前端展示表
 *
 * @author wanjie
 * @date 2025-08-27 11:45
 * @version 1.0
 */
@Data
public class UserTagValueTableVO {

    @Schema(description = "表头")
    private List<TagParam> headItem;

    @Schema(description = "数据")
    private List<TagValueParam> valueItem;

    @Schema(description = "总记录数")
    private Long total;

    @Schema(description = "当前页")
    private Long current;

    @Schema(description = "每页记录数")
    private Long size;

    @Schema(description = "总页数")
    private Long pages;

}
