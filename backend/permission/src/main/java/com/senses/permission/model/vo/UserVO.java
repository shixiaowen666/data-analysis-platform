package com.senses.permission.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.senses.permission.entity.Application;
import com.senses.permission.entity.Group;
import lombok.Data;

import java.util.Date;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 用户表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "用户信息")
@Data
public class UserVO {
    
    /** ID */
    @Schema(description = "ID")
    private Long id;
    
    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;
    

    /** 用户名 */
    @Schema(description = "用户名")
    private String username;

     /** 姓名 */
     @Schema(description = "姓名")
     private String name;


    /** 手机号 */
    @Schema(description = "手机号")
    private String phone;
    
    /** 部门id */
    @Schema(description = "部门id")
    private Long deptId;


    /** 是否管理员 0否1是 */
    @Schema(description = "是否管理员 0否1是")
    private Integer isAdmin;
    
    /** 状态 0禁用1启用2已删除 */
    @Schema(description = "状态 0禁用1启用2已删除（删除状态不会返给前端）")
    private Integer status;
    
    /** 创建时间 */
    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;


    /** 更新时间 */
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;


}