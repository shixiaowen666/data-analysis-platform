package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author wanjie
 */
@Data
public class TagParam {
    private Long id;
    /** 标签名称 */
    @Schema(description = "标签名称")
    private String tagName;

    /** 标签描述 */
    @Schema(description = "标签描述")
    private String description;

    /** 是否删除 0否1是 */
    @Schema(description = "是否删除 0否1是")
    private Integer isDeleted;

}
