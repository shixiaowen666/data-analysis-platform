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
 * 用户查询引擎配置表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "用户查询引擎配置表")
@TableName("user_engine_config")
@Data
public class UserEngineConfig{
    
    /**  */
    @Schema(description = "")
    @TableId(value = "id",type= IdType.AUTO)
    private Integer id;
    
    /** 用户id */
    @Schema(description = "用户id")
    @TableField("usr_id")
    private Integer usrId;
    
    /** 引擎 */
    @Schema(description = "引擎")
    @TableField("engine_config")
    private String engineConfig;
}