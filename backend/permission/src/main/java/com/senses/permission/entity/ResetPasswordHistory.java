package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 重置密码发送邮箱链接记录表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "重置密码发送邮箱链接记录表")
@TableName("reset_password_history")
@Data
public class ResetPasswordHistory{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 重置密码链接 */
    @Schema(description = "重置密码链接")
    @TableField("reset_password_url")
    private String resetPasswordUrl;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 创建人 */
    @Schema(description = "创建人")
    @TableField("create_user")
    private String createUser;
    
    /** 过期时间 */
    @Schema(description = "过期时间")
    @TableField("expire_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;
    
    /** 用户id */
    @Schema(description = "用户id")
    @TableField("user_id")
    private Long userId;
    
    /** 接收邮箱 */
    @Schema(description = "接收邮箱")
    @TableField("email")
    private String email;

}