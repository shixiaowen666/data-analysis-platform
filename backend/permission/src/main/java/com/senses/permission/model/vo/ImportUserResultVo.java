package com.senses.permission.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description
 * @Date 2025-02-06 15:03
 * @Author liaojinlei
 **/
@Data
public class ImportUserResultVo implements Serializable {

    /** 导入总数 */
    @Schema(description = "导入总数")
    private Integer totalAddUserCount;

    /** 导入成功数量 */
    @Schema(description = "导入成功数量")
    private Integer addSuccessCount;

    /** 导入失败数量 */
    @Schema(description = "导入失败数量")
    private Integer addFailedCount;

    /** 导入失败数量 */
    @Schema(description = "导入失败用户信息")
    private List<ImportUserFailedVo> importUserFailedVos;

}
