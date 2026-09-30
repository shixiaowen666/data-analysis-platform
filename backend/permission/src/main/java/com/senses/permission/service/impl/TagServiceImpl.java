package com.senses.permission.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.senses.permission.entity.Tag;
import com.senses.permission.entity.User;
import com.senses.permission.entity.UserTagValue;
import com.senses.permission.mapper.TagMapper;
import com.senses.permission.mapper.UserTagValueMapper;
import com.senses.permission.model.PageParam;
import com.senses.permission.model.ResultData;
import com.senses.permission.model.param.TagParam;
import com.senses.permission.model.param.TagValueParam;
import com.senses.permission.model.param.UserPageParam;
import com.senses.permission.model.param.UserTagValueParam;
import com.senses.permission.model.vo.UserTagValueTableVO;
import com.senses.permission.service.TagService;
import com.senses.permission.service.UserService;
import com.senses.permission.service.UserTagValueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author wanjie
 */
@Service
@Slf4j
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    @Autowired
    private TagMapper tagMapper;

    @Autowired
    private UserTagValueService userTagValueService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserTagValueMapper userTagValueMapper;

    @Override
    public ResultData<List<TagParam>> getAllTags() {
        return ResultData.success(tagMapper.selectAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList()));
    }


    @Transactional(rollbackFor = Exception.class)
    @Override
    public ResultData addOrModifyTag(TagParam tagParam, String username) {
        if (Objects.equals(tagParam.getIsDeleted(), 1) && Objects.nonNull(tagParam.getId())) {

            // todo 检查标签是否已被关联
//            if (userTagValueService.countByTagId(tagDTO.getId()) > 0) {
//                return ResultData.fail("该标签已被关联，无法删除");
//            }
            removeById(tagParam.getId());
            // 删除关联标签值
            userTagValueService.deleteByTagId(tagParam.getId());
            return ResultData.success("删除成功");
        } else if (Objects.nonNull(tagParam.getId())) {
            LambdaQueryWrapper<Tag> lambdaQueryWrapper = new LambdaQueryWrapper<>();
            lambdaQueryWrapper.eq(Tag::getId, tagParam.getId());
            lambdaQueryWrapper.eq(Tag::getIsDeleted, 0);
            Tag tag = getOne(lambdaQueryWrapper);
            if (Objects.isNull(tag)) {
                return ResultData.fail("标签不存在！");
            }
        }

        Tag tag = new Tag();
        BeanUtils.copyProperties(tagParam, tag);
        Tag tagBase = baseMapper.selectByTagName(tagParam.getTagName());
        // 更新
        if (Objects.nonNull(tag.getId())) {
            if (tagBase != null && !Objects.equals(tagParam.getId(), tagBase.getId())) {
                return ResultData.fail("标签名称已存在");
            }
        } else {
            tag.setCreatedTime(new Date());
            if (tagBase != null) {
                return ResultData.fail("标签名称已存在");
            }
        }
        tag.setUpdatedTime(new Date());
        tag.setIsDeleted(0);
        saveOrUpdate(tag);
        return ResultData.success("添加成功");
    }

    @Override
    public Tag getById(Long id) {
        return tagMapper.selectById(id);
    }

    @Override
    public ResultData<List<UserTagValue>> getUserTagValuesByUserId(Long userId) {
        User user = userService.getById(userId);
        List<UserTagValue> userTagValues = userTagValueMapper.selectByUsername(user.getUsername());
        return ResultData.success(userTagValues);
    }


    @Override
    public ResultData<UserTagValueTableVO> getUserTagValuesTable(String keyword, Integer pageNo, Integer pageSize) {

        UserTagValueTableVO userTagValueTableVO = new UserTagValueTableVO();
        // 表头
        List<TagParam> headItem = baseMapper.selectAll().stream()
                .sorted(Comparator.comparing(Tag::getId))
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        userTagValueTableVO.setHeadItem(headItem);

        // 数据
        PageParam<UserPageParam> pageParam = new PageParam<>();
        pageParam.setPage(new Page<>(pageNo, pageSize));
        pageParam.setQueryParam(new UserPageParam());
        pageParam.getQueryParam().setFilterVal(keyword);
        Page<User> page = userService.listByPage(pageParam);
        
        // 过滤掉 username 为 admin 的数据
        List<User> filteredUsers = page.getRecords().stream()
                .filter(user -> !("admin".equals(user.getUsername())))
                .collect(Collectors.toList());

        userTagValueTableVO.setTotal((long) filteredUsers.size());
        userTagValueTableVO.setCurrent(page.getCurrent());
        userTagValueTableVO.setSize(page.getSize());
        userTagValueTableVO.setPages(page.getPages());

        List<String> usernames = filteredUsers.stream()
                .map(User::getUsername)
                .collect(Collectors.toList());

        if (!usernames.isEmpty()) {
            List<UserTagValue> userTagValues = userTagValueMapper.selectByUsernames(usernames);
            Map<String, List<UserTagValue>> userTagValueMap = userTagValues.stream()
                    .collect(Collectors.groupingBy(UserTagValue::getUsername));
            // 生成完整用户标签值列表
            List<TagValueParam> userTagValueParams = generateUserTagValueParams(filteredUsers, userTagValueMap);

            userTagValueTableVO.setValueItem(userTagValueParams);
        } else {
            userTagValueTableVO.setValueItem(Collections.emptyList());
        }

        return ResultData.success(userTagValueTableVO);
    }

    private List<TagValueParam> generateUserTagValueParams(
            List<User> users,
            Map<String, List<UserTagValue>> userTagValueMap) {

        return users.stream()
                .map(user -> {
                    TagValueParam tagValueParam = new TagValueParam();
                    tagValueParam.setUsername(user.getUsername());
                    tagValueParam.setName(user.getName());

                    // 获取用户标签值，如果没有则返回空列表
                    List<UserTagValueParam> tagValueList = Optional.ofNullable(userTagValueMap.get(user.getUsername()))
                            .map(list -> list.stream()
                                    .map(this::convertToDTO)
                                    .collect(Collectors.toList()))
                            .orElse(Collections.emptyList());

                    tagValueParam.setTagValueList(tagValueList);
                    return tagValueParam;
                })
                .collect(Collectors.toList());
    }

    private TagParam convertToDTO(Tag tag) {
        TagParam dto = new TagParam();
        BeanUtils.copyProperties(tag, dto);
        return dto;
    }

    private UserTagValueParam convertToDTO(UserTagValue userTagValue) {
        UserTagValueParam dto = new UserTagValueParam();
        BeanUtils.copyProperties(userTagValue, dto);
        return dto;
    }
}
