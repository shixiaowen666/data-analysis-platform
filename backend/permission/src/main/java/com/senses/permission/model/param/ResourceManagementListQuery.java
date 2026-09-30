package com.senses.permission.model.param;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/***
 * @ClassName ResourceManagementQuery
 * @Description
 * @Author chenxiwen
 * @Date 9/4/24 8:43 PM
 * @Version 1.0
 */
@Data
public class ResourceManagementListQuery {

    @Schema(description = "资源类型")
    private Integer resourceType;

    @Schema(description = "源系统对象id")
    private Long objectId;

    @Schema(description = "关联应用Code")
    private String applicationCode;

    @Schema(description = "授权用户id")
    private Long authorizedUserId;


    @Schema(description = "授权对象类别 1.用户2用户组3.部门")
    private Integer authorizedObjectType;

    @Schema(description = "访问权限(1-查看访问,2-编辑访问,3-完全访问)")
    private List<Integer> authLevelList;

    @Schema(description = "下载权限 1-有 0-无 -1-不涉及")
    private List<Integer> authDownloadList;


}
