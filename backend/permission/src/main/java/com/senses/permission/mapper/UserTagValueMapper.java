package com.senses.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UserTagValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户标签值
 *
 * @author wanjie
 * @version 1.0
 * @date 2025-08-26 15:09
 */
@Mapper
public interface UserTagValueMapper extends BaseMapper<UserTagValue> {
    /**
     * 根据标签ID删除用户标签值
     *
     * @param tagId 标签ID
     */
    void deleteByTagId(@Param("tagId") Long tagId);

    /**
     * 根据标签ID和用户名删除用户标签值
     *
     * @param tagId    标签ID
     * @param username 用户名
     */
    void deleteByTagIdAndUsername(@Param("tagId") Long tagId, @Param("username") String username);

    List<UserTagValue> selectByUsername(@Param("username") String username);

    List<UserTagValue> selectByUsernames(@Param("usernames") List<String> usernames);

}


