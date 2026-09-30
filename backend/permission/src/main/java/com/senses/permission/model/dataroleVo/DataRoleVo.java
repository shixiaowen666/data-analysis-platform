package com.senses.permission.model.dataroleVo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;
@Data
@Schema(description = "数据角色VO")
public class DataRoleVo {
    @Schema(description = "ID",example = "1212")
    private Long id;
    @Schema(description = "角色名称")
    private String name;
    @Schema(description = "角色中文名称")
    private String remark;
    @Schema(description = "部门id",example = "1212")
    private Long deptId;
    @Schema(description = "部门名称")
    private String deptName;
    @Schema(description = "数据权限列表（更新时删除的权限可从列表移除，不必回传）")
    private Set<DataPermissionVo> dataPermissions;


    /** 状态0禁用1启用2删除 */
    @Schema(description = "状态0禁用1启用2删除")
    private Integer status;

    /** 创建日期 */
    @Schema(description = "创建日期")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdTime;

    /** 创建者 */
    @Schema(description = "创建者")
    private String createdUser;

    /** 修改人 */
    @Schema(description = "修改人")
    private String modifyUser;

    /** 更新时间 */
    @Schema(description = "更新时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date modifyTime;


    /**审批状态 0审批中 1审批通过 2审批不通过 */
    @Schema(description = "审批状态 0未审批 1审批通过 2审批不通过 3已撤回")
    @TableField(exist = false)
    private Integer approveStatus;
}
