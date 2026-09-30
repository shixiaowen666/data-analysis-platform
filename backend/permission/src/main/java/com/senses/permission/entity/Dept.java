package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;

 /**
 * 部门表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "部门表")
@TableName("dept")
@Data
public class Dept implements Comparable<Dept>{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 名称 */
    @Schema(description = "名称")
    @TableField("name")
    private String name;
    
    /** 部门编码 */
    @Schema(description = "部门编码")
    @TableField("code")
    private String code;
    
    /** 上级部门 */
    @Schema(description = "上级部门")
    @TableField("pid")
    private Long pid;

    /** 状态0禁用1启用 */
    @Schema(description = "状态0禁用1启用")
    @TableField("status")
    private Integer status;
    
    /** 创建时间 */
    @Schema(description = "创建时间")
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

    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;


     @Override
     public int compareTo(Dept o) {
         int result = this.getPid().compareTo(o.getPid());
         if(result!=0){
             return result;
         }else{
             return this.getId().compareTo(o.getId());
         }
     }
}