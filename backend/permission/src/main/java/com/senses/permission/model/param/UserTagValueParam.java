package com.senses.permission.model.param;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @author wanjie
 */
@Data
public class UserTagValueParam {
    private Long id;

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

    /** 标签ID */
    @Schema(description = "标签ID")
    private Long tagId;

    /** 标签名称 */
    @Schema(description = "标签名称")
    private String tagName;

    /** 标签值 */
    @Schema(description = "标签值")
    private String tagValue;


}
