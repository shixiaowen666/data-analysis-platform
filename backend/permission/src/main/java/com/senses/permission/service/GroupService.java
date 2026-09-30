package com.senses.permission.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.senses.permission.entity.ApproveRecord;
import com.senses.permission.entity.DataRole;
import com.senses.permission.entity.Group;
import com.senses.permission.entity.Role;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.TreeData;
import com.senses.permission.model.param.GroupAddUsersParam;
import com.senses.permission.model.param.GroupPageParam;
import com.senses.permission.model.param.GroupParam;
import com.senses.permission.model.vo.GroupRolesIdsVO;

import java.util.List;


/**
 * 用户组表;(application)表服务接口
 *
 * @author : shinan
 * @date : 2022-12-8
 */
public interface GroupService extends IService<Group> {

    ResultData saveOrUpdateGroup(GroupParam groupParam, String userName);

    ResultData deleteGroup(Long groupId, String userName);

    List<Group> listLikeGroupname(String groupName);

    Page<Group> listByPage(PageParam<GroupPageParam> pageParam);

    Group getInfo(Integer groupId);

    ResultData bind(Long groupId, List<Long> roleIds, List<Long> dataRoleIds,String username);

    ResultData addUsers(GroupAddUsersParam groupAddUsersParam);

    List<TreeData<Role>> roleTreeList(Long groupId);

    List<TreeData<DataRole>> dataRoleTreeList(Long groupId);

    GroupRolesIdsVO getRoleAndDataRoleIds(Long groupId);

    void confirmApprove(ApproveRecord approveRecord);

    List<Group> getGroupsByUsernames(List<String> usernames);

    List<TreeData<Object>> getGroupUsersTree();
}