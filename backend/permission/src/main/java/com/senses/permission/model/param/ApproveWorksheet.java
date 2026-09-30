package com.senses.permission.model.param;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.senses.permission.constant.RoleTypeEnum;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.Permission;
import com.senses.permission.entity.Role;
import com.senses.permission.model.dataroleVo.DataPermissionVo;
import lombok.Data;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class ApproveWorksheet {
    @Schema(description = "源系统三方id")
    private String thirdId;
    @Schema(description = "工单名称")
    private String worksheetName;
    @Schema(description = "工单详情信息 jsonArray格式")
    private String worksheetInfo;
    @Schema(description = "创建者")
    private String creator;
    @Schema(description = "流程应用绑定标识")
    private String bindMark;

    public ApproveWorksheet(){

    }

    public ApproveWorksheet(String thirdId,String worksheetName,String creator,String worksheetInfo,String bindMark){
        this.thirdId = thirdId;
        this.worksheetName = worksheetName;
        this.worksheetInfo = worksheetInfo;
        this.creator = creator;
        this.bindMark = bindMark;
    }

    public static String createRolesAndDataRoleWorksheetData(List<Role> roles, List<DataRole> dataRoles) {
        JSONArray allJsonArray = new JSONArray();
        if(!CollectionUtils.isEmpty(roles)){
            for(Role role:roles){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("roleName",role.getName());
                jsonObject.put("roleCnName",role.getCnName());
                jsonObject.put("appName",role.getAppName());
                //角色类型 0应用管理员1应用成员
                jsonObject.put("type",role.getType());
                //角色分类 0应用角色，1数据角色
                jsonObject.put("roleClasz",0);
                jsonObject.put("deptName","");
                allJsonArray.add(jsonObject);
            }
        }
        if(!CollectionUtils.isEmpty(dataRoles)){
            for(DataRole dateRole:dataRoles){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("roleName",dateRole.getName());
                jsonObject.put("roleCnName",dateRole.getRemark());
                jsonObject.put("appName","");
                //角色类型 0管理员1成员
                jsonObject.put("type",null);
                //角色分类 0应用角色，1数据角色
                jsonObject.put("roleClasz",1);
                jsonObject.put("deptName",dateRole.getName());
                allJsonArray.add(jsonObject);
            }
        }
        return allJsonArray.toJSONString();
    }

    public static String createPermissionsWorksheetData(Integer roleType, List<Permission> pmsList) {
        JSONArray jsonArray = new JSONArray();
        if(RoleTypeEnum.APP_ADMIN.getId() == roleType){
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("appName","*");
            jsonObject.put("permissionName","*");
            jsonObject.put("sign","*");
            jsonObject.put("menuType","*");
            jsonObject.put("buttonType","*");
            jsonObject.put("type","*");
            jsonObject.put("externalUrl","*");
            jsonArray.add(jsonObject);
        }else {
            if(CollectionUtils.isEmpty(pmsList)){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("appName","");
                jsonObject.put("permissionName","解绑所有权限");
                jsonObject.put("sign","");
                jsonObject.put("menuType","");
                jsonObject.put("buttonType","");
                jsonObject.put("type","");
                jsonObject.put("externalUrl","");
                jsonArray.add(jsonObject);
            }else{
                for(Permission permission:pmsList){
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("appName",permission.getAppName());
                    jsonObject.put("permissionName",permission.getName());
                    jsonObject.put("sign",permission.getSign());
                    jsonObject.put("menuType",permission.getMenuType());
                    jsonObject.put("buttonType",permission.getButtonType());
                    jsonObject.put("type",permission.getType());
                    jsonObject.put("externalUrl",permission.getExternalUrl());
                    jsonArray.add(jsonObject);
                }
            }

        }
        return jsonArray.toJSONString();
    }

    public static String createDataPermissionsWorksheetData(Set<DataPermissionVo> dataPermissions) {
        if(CollectionUtils.isEmpty(dataPermissions)){
            return new JSONArray().toJSONString();
        }else {
            JSONArray jsonArray = new JSONArray();
            for(DataPermissionVo dataPermissionVo:dataPermissions){
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("engine",dataPermissionVo.getEngine());
                jsonObject.put("datasourceName",dataPermissionVo.getDatasourceName());
                jsonObject.put("databaseName",dataPermissionVo.getDatabaseName());
                jsonObject.put("tableName",dataPermissionVo.getTableName());
                jsonObject.put("columnName",dataPermissionVo.getColumnName());
                jsonObject.put("action",dataPermissionVo.getAction());
                jsonArray.add(jsonObject);
            }
            return jsonArray.toJSONString();
        }
    }
}
