package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户标签数据类
 *
 * @author wanjie
 * @date 2025-08-27 11:52
 * @version 1.0
 */

@Data
public class TagValueParam {

    @Schema(description = "姓名")
    private String name;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "标签值列表")
    private List<UserTagValueParam> tagValueList;
}
