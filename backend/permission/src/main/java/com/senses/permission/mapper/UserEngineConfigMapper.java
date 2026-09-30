package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.UserEngineConfig;

 /**
 * 用户查询引擎配置表;(user_engine_config)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface UserEngineConfigMapper extends BaseMapper<UserEngineConfig>{
}