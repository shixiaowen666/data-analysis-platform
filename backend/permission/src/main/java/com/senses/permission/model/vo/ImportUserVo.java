package com.senses.permission.model.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.io.Serializable;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @Description
 * @Date 2025-02-06 15:03
 * @Author liaojinlei
 **/
@Data
public class ImportUserVo implements Serializable {

    /** 用户名 */
    @Schema(description = "用户名")
    @ExcelProperty(value = "用户名")
    private String username;

    /** 姓名 */
    @Schema(description = "用户中文名")
    @ExcelProperty(value = "用户中文名")
    private String name;

    /** 手机号 */
    @Schema(description = "手机号")
    @ExcelProperty(value = "手机号")
    private String phone;

    /** 邮箱 */
    @Schema(description = "邮箱")
    @ExcelProperty(value = "邮箱")
    private String email;

    /** 初始密码 */
    @Schema(description = "初始密码")
    @ExcelProperty(value = "初始密码")
    private String password;

    /** 所属部门 */
    @Schema(description = "所属部门链")
    @ExcelProperty(value = "所属部门链，上下级部门以“_”连接")
    private String deptLink;

}
