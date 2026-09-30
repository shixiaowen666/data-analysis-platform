package com.senses.permission.model.dataroleVo;

import lombok.Data;

import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <p>
 * 资源管理表
 * </p>
 *
 * @author dd
 * @since 2024-09-25
 */

@Schema(description = "资源权限参数VO")
@Data
public class ResourceManagementVo {
    @Schema(description = "资源id，更新时必传；新增时不传")
    private Long id;

    @Schema(description = "资源类型(0-报表 1-指标 2-仪表盘)")
    private Integer resourceType;


    @Schema(description = "源系统对象id")
    private Long objectId;


    @Schema(description = "对象名称")
    private String objectName;


    @Schema(description = "关联应用Code")
    private String applicationCode;


    @Schema(description = "授权对象类别 1.用户2用户组3.部门")
    private Integer authorizedObjectType;

    @Schema(description = "授权对象id")
    private Long authorizedObjectId;

    @Schema(description = "授权对象名称")
    private String authorizedObjectName;

    @Schema(description = "访问权限(1-查看访问,2-编辑访问,3-完全访问(具有授权权限))")
    private Set<Integer> authLevel;

    @Schema(description = "最大访问权限(1-查看访问,2-编辑访问,3-完全访问(具有授权权限))")
    private Integer maxAuthLevel;

    @Schema(description = "下载权限 1-有 0-无 -1-不涉及")
    private Integer authDownload;


    @Schema(description = "授权人id")
    private Long authorizeUserId;

    @Schema(description = "状态（0-失效,1-生效）")
    private Integer onlineStatus;


}
