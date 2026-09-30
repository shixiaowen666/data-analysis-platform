package com.senses.permission.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.TableField;
import java.io.Serializable;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 资源管理表
 * </p>
 *
 * @author dd
 * @since 2024-09-25
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("resource_management")
public class ResourceManagement {

    private static final long serialVersionUID=1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 资源类型(0-报表 1-指标 2-仪表盘)
     */
    @TableField("resource_type")
    private Integer resourceType;

    /**
     * 源系统对象id
     */
    @TableField("object_id")
    private Long objectId;

    /**
     * 对象名称
     */
    @TableField("object_name")
    private String objectName;

    /**
     * 关联应用id
     */
    @TableField("application_code")
    private String applicationCode;

    /**
     * 授权对象类别 1.用户2用户组3.部门
     */
    @TableField("authorized_object_type")
    private Integer authorizedObjectType;

    /**
     * 授权对象id
     */
    @TableField("authorized_object_id")
    private Long authorizedObjectId;

    @Schema(description = "被授权用户组名")
    @TableField(exist = false)
    private String authorizedUserGroupName;
    @Schema(description = "被授权用户名")
    @TableField(exist = false)
    private String authorizedObjectName;
    @Schema(description = "被授权用户中文名")
    @TableField(exist = false)
    private String authorizedObjectNameCn;

    /**
     * 访问权限(1-查看访问,2-编辑访问,3-完全访问(具有授权权限))
     */
    @TableField("auth_level")
    private String authLevel;
    @TableField(exist = false)
    private Integer authLevelVal;


    /**
     * 下载权限 1-有 0-无 -1-不涉及
     */
    @TableField("auth_download")
    private Integer authDownload;

    /**
     * 授权人id
     */
    @TableField("authorize_user_id")
    private Long authorizeUserId;

    @TableField(exist = false)
    private String authorizeUserName;
    @TableField(exist = false)
    private String authorizeNameCn;
    /**
     * 授权时间
     */
    @TableField("auth_time")
    private LocalDateTime authTime;

    /**
     * 修改时间
     */
    @TableField("modify_time")
    private LocalDateTime modifyTime;

    /**
     * 修改人id
     */
    @TableField("modify_user_id")
    private Long modifyUserId;
    //所属组织
    @TableField(exist = false)
    private String modifyUserName;
    //所属组织
    @TableField(exist = false)
    private String modifyUserNameCn;

    /**
     * 状态（0-失效,1-生效）
     */
    @TableField("online_status")
    private Integer onlineStatus;

    //所属组织
    @TableField(exist = false)
    private String deptName;

    public boolean implies(ResourceManagement dp ){
        if(this.applicationCode!=null &&!this.applicationCode.equals(dp.applicationCode)){
            return false;
        }
        if(this.resourceType!=dp.getResourceType()){
            return false;
        }
        if(this.objectId!=dp.getObjectId()){
            return false;
        }
        // if(this.authLevel!=3&&this.authLevel!=dp.getAuthLevel()){
        //     return false;
        // }
        if(this.authDownload!=1 && dp.getAuthDownload()==1){
            return false;
        }
        return true;
    }


        public static final String ID = "id";

    public static final String RESOURCE_TYPE = "resource_type";

    public static final String OBJECT_ID = "object_id";

    public static final String OBJECT_NAME = "object_name";

    public static final String APPLICATION_ID = "application_id";

    public static final String AUTHORIZED_OBJECT_TYPE = "authorized_object_type";

    public static final String AUTHORIZED_OBJECT_ID = "authorized_object_id";

    public static final String AUTH_LEVEL = "auth_level";

    public static final String AUTH_DOWNLOAD = "auth_download";

    public static final String AUTHORIZE_USER_ID = "authorize_user_id";

    public static final String AUTH_TIME = "auth_time";

    public static final String MODIFY_TIME = "modify_time";

    public static final String MODIFY_USER = "modify_user";

    public static final String ONLINE_STATUS = "online_status";

    protected Serializable pkVal() {
        return this.id;
    }

}
