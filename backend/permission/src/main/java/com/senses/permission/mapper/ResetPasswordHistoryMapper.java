package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.ResetPasswordHistory;

 /**
 * 重置密码发送邮箱链接记录表;(reset_password_history)表数据库访问层
 * @author : liaojinlei
 * @date : 2022-12-7
 */
@Mapper
public interface ResetPasswordHistoryMapper extends BaseMapper<ResetPasswordHistory>{
}