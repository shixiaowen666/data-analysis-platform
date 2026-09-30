package com.senses.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.poi.ss.usermodel.DateUtil;

/**
 * 数据角色与权限关系表;
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Schema(description = "数据角色与权限关系表")
@TableName("data_roles_permission")
@Data
@Slf4j
public class DataRolesPermission{
    
    /** ID */
    @Schema(description = "ID")
    @TableId(value = "id",type= IdType.AUTO)
    private Long id;
    
    /** 数据角色ID */
    @Schema(description = "数据角色ID")
    @TableField("data_role_id")
    private Long dataRoleId;
    
    /** 引擎ID */
    @Schema(description = "引擎ID")
    @TableField("engine_id")
    private Long engineId;
    
    /** 引擎 */
    @Schema(description = "引擎")
    @TableField("engine")
    private String engine;
    
    /** 数据源ID */
    @Schema(description = "数据源ID")
    @TableField("datasource_id")
    private Long datasourceId;
    
    /** 表名 */
    @Schema(description = "表名")
    @TableField("table_name")
    private String tableName;
    
    /** 列名 */
    @Schema(description = "列名")
    @TableField("column_name")
    private String columnName;
    
    /** 操作权限0读1写2读写 */
    @Schema(description = "操作权限0读1写2读写")
    @TableField("action")
    private Integer action;
    
    /** 数据源 */
    @Schema(description = "数据源")
    @TableField("datasource_name")
    private String datasourceName;
    
    /** 数据库 */
    @Schema(description = "数据库")
    @TableField("database_name")
    private String databaseName;
    
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
    
    /** 截止时间 */
    @Schema(description = "截止时间")
    @TableField("deadline_time")
    @JsonFormat(pattern = "yyyy-MM-dd")
//    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date deadlineTime;

    public boolean implies(DataRolesPermission dp){

         //是否过期
         Date deadlineTime = this.getDeadlineTime();
         Date nowDate = new Date();
         if(null != deadlineTime){
             if (nowDate.getTime()> deadlineTime.getTime()) {
                 return false;
             }
         }

         if (!((this.action & dp.getAction()) == dp.getAction())) {
             return false;
         }


         if(!this.engine.equalsIgnoreCase(dp.getEngine())){
             return false;
         }

         if(!this.databaseName.equalsIgnoreCase(dp.getDatabaseName())){
             return false;
         }

         if(this.tableName.equals("*")){
             return true;
         }

         if(!this.tableName.equalsIgnoreCase(dp.getTableName()) &&
                 !this.tableName.equalsIgnoreCase(dp.getDatabaseName() + "." + dp.getTableName())){
             return false;
         }

         if(this.columnName.equals("*")){
             return true;
         }

         if(!this.columnName.equalsIgnoreCase(dp.getColumnName())){
             return false;
         }

         return true;

     }
     public boolean checkAndCopy(DataRolesPermission p){
         boolean flag = true;
         if(this.id!=p.getId()){
             this.id=p.getId();
             flag= false;
         }
         if(this.dataRoleId!=p.getDataRoleId()){
             this.dataRoleId = p.getDataRoleId();
             flag= false;
         }
         if(this.engineId!=p.getEngineId()){
             this.engineId = p.getEngineId();
             flag= false;
         }
         if(!this.engine.equals(p.getEngine())){
             this.engine = p.getEngine();
             flag= false;
         }
         if(this.datasourceId!=p.getDatasourceId()){
             this.dataRoleId = p.getDataRoleId();
             flag= false;
         }
         if(!this.datasourceName.equals(p.getDatasourceName())){
             this.datasourceName = p.getDatasourceName();
             flag= false;
         }
         if(!this.databaseName.equals(p.getDatabaseName())){
             this.databaseName =p.getDatabaseName();
             flag= false;
         }
         if(!this.tableName.equals(p.getTableName())){
             this.tableName= p.getTableName();
             flag= false;
         }
         if(!this.columnName.equals(p.getColumnName())){
             this.columnName= p.getColumnName();
             flag= false;
         }
         if(this.action!=p.getAction()){
             this.action = p.getAction();
             flag= false;
         }
         if(!this.deadlineTime.equals(p.getDeadlineTime())){
             this.deadlineTime = p.getDeadlineTime();
             flag= false;
         }
         return flag;
     }
}