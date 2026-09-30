package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * 权限表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "权限表")
@TableName("permission")
@Data
public class Permission{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 名称 */
    @Schema(description = "名称")
    @TableField("name")
    private String name;
    
    /** 上级权限 */
    @Schema(description = "上级权限")
    @TableField("pid")
    private Long pid;
    
    /** 所属应用id */
    @Schema(description = "所属应用id")
    @TableField("app_id")
    private Long appId;

     /** 所属应用名称 */
     @Schema(description = "所属应用名称")
     @TableField(exist = false)
     private String appName;
    
    /** 功能标识，当功能类型为0菜单时，此处为菜单链接 */
    @Schema(description = "功能标识，当功能类型为0菜单时，此处为菜单链接")
    @TableField("sign")
    private String sign;
    
    /** 功能类型 0菜单menu，1按钮button，2链接source*/
    @Schema(description = "功能类型 0菜单menu，1按钮button，2链接source")
    @TableField("type")
    private Integer type;
    
    /** 状态0无效已删除，1有效 */
    @Schema(description = "状态0无效已删除，1有效")
    @TableField("status")
    private Integer status;
    
    /** 创建日期 */
    @Schema(description = "创建日期")
    @TableField("created_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;
    
    /** 创建人 */
    @Schema(description = "创建人")
    @TableField("created_user")
    private String createdUser;
    
    /** 修改时间 */
    @Schema(description = "修改时间")
    @TableField("modify_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;
    
    /** 修改人 */
    @Schema(description = "修改人")
    @TableField("modify_user")
    private String modifyUser;

     /** 菜单类型 0外部菜单，1内部菜单*/
     @Schema(description = "菜单类型 0外部菜单，1内部菜单")
     @TableField("menu_type")
     private Integer menuType;

     /** 按钮类型 0功能性按钮，1外部链接按钮，2内部链接按钮*/
     @Schema(description = "按钮类型 0功能性按钮，1外部链接按钮，2内部链接按钮")
     @TableField("button_type")
     private Integer buttonType;

     /** 外部链接 */
     @Schema(description = "外部链接")
     @TableField("external_url")
     private String externalUrl;

     /** 序号*/
     @Schema(description = "序号")
     @TableField("order_num")
     private Integer orderNum;


     /** 创建信息 */
     @Schema(description = "创建信息")
     @TableField(exist = false)
     private String createdInfo;

     /** 选中状态 0否1是 */
     @Schema(description = "选中状态 0否1是")
     @TableField(exist = false)
     private Integer selected;

     /** 是否有下级 0否1是 */
     @Schema(description = "是否有下级 0否1是")
     @TableField(exist = false)
     private Integer isChild;

     /** 更改信息 */
     @Schema(description = "更改信息")
     @TableField(exist = false)
     private String modifyInfo;
     public String getCreatedInfo(){

         return "";
     }

     public String getModifyInfo(){

         return "";
     }
}