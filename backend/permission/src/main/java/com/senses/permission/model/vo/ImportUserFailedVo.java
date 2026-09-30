package com.senses.permission.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @Description
 * @Date 2025-02-07 10:10
 * @Author liaojinlei
 **/
@Data
public class ImportUserFailedVo implements Serializable{

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "导入失败原因")
    private String errorMsg;
}
