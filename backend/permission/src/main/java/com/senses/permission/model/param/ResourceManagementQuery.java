package com.senses.permission.model.param;

import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;



/***
 * @ClassName ResourceManagementQuery
 * @Description
 * @Author chenxiwen
 * @Date 9/4/24 8:43 PM
 * @Version 1.0
 */
@Schema(description = "资源管理页面过滤参数")
@Data
public class ResourceManagementQuery {

    @Schema(description = "资源类型")
    private List<String> resourceTypeList;

    @Schema(description = "对象名称关键字")
    private String keywords;

    @Schema(description = "关联应用Code")
    private String applicationCode;

    @Schema(description = "被授权用户id（按组织查询使用）")
    private List<Long> authorizedUserIdList;

    @Schema(description = "所属组织id（按组织查询使用）")
    private List<Long> belongDeptIdList;

    @Schema(description = "被授权用户组id（按用户组查询使用）")
    private List<Long> authorizedUserGroupIdList;

    @Schema(description = "访问权限(1-查看访问,2-编辑访问,3-完全访问)")
    private List<Integer> authLevelList;

    @Schema(description = "下载权限 1-有 0-无 -1-不涉及")
    private List<Integer> authDownloadList;

    @Schema(description = "授权人id")
    private List<Long> authorizerIdList;


    @Schema(description = "开始时间(筛选授权时间)")
    private String authTimeBegin;

    @Schema(description = "结束时间(筛选授权时间)")
    private String authTimeEnd;

    @Schema(description = "开始时间(筛选修改时间)")
    private String modifyTimeBegin;

    @Schema(description = "结束时间(筛选修改时间)")
    private String modifyTimeEnd;

    @Schema(description = "修改人id")
    private List<Long> modifierIdList;

    /**
     * 状态（0-失效,1-生效）
     */
    @Schema(description = "状态（1-生效,2-删除)")
    private Integer onlineStatus;

}
