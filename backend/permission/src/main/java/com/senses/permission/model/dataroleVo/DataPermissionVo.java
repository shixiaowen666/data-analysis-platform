package com.senses.permission.model.dataroleVo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.util.Date;
import io.swagger.v3.oas.annotations.media.Schema;
@Data
@Schema(description = "数据权限VO")
public class DataPermissionVo {
    /** ID */
    @Schema(description = "权限id(新增时不传)",example = "1212")
    private Long id;

    /** 数据角色ID */
    @Schema(description = "数据角色id",example = "1212")
    private Long dataRoleId;

    /** 引擎ID */
    @Schema(description = "引擎ID",example = "1212")
    private Long engineId;

    /** 引擎 */
    @Schema(description = "引擎")
    private String engine;

    /** 数据源ID */
    @Schema(description = "数据源ID",example = "1212")
    private Long datasourceId;

    /** 表名 */
    @Schema(description = "表名")
    private String tableName;

    /** 列名 */
    @Schema(description = "列名")
    private String columnName;

    /** 操作权限0读1写2读写 */
    @Schema(description = "操作权限0读1写2读写",example = "1212")
    private Integer action;

    /** 数据源 */
    @Schema(description = "数据源名称")
    private String datasourceName;

    /** 数据库 */
    @Schema(description = "数据库名称")
    private String databaseName;

    /** 状态 */
    @Schema(description = "状态:0未启用1启用2删除",example = "1212")
    private Integer status;

    /** 创建日期 */
    @Schema(description = "创建日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;

    /** 创建人 */
    @Schema(description = "创建人")
    private String createdUser;

    /** 修改人 */
    @Schema(description = "修改人")
    private String modifyUser;

    /** 修改时间 */
    @Schema(description = "修改时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;

    /** 截止时间 */
    @Schema(description = "截止时间")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date deadlineTime;

}
