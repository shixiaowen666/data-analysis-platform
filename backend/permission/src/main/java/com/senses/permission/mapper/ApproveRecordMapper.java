package com.senses.permission.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.senses.permission.entity.ApproveRecord;
import org.apache.ibatis.annotations.Select;

/**
 * 审批记录表;(approve_record)表数据库访问层
 * @author : liaojinlei
 * @date : 2023-1-11
 */
@Mapper
public interface ApproveRecordMapper extends BaseMapper<ApproveRecord>{
    @Select({"select * ",
            " from approve_record ",
            " where flow_tag= #{flowTag} and approve_target_id= #{approveTargetId}",
            " order by id desc",
            " limit 1"
        }
    )
     ApproveRecord lastByFlagAndTargetId(@Param("flowTag") String flowTag, @Param("approveTargetId") Long approveTargetId);
    @Select({"select * ",
            " from approve_record ",
            " where approve_target_id= #{approveTargetId}",
            " order by id desc",
            " limit 1"
    }
    )
    ApproveRecord selectByTargetId(@Param("approveTargetId") Long approveTargetId);

}