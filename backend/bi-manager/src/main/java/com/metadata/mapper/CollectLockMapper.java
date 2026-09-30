package com.metadata.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MySQL 数据库锁 Mapper
 */
@Mapper
public interface CollectLockMapper {

    @Select("SELECT GET_LOCK(#{lockName}, 0)")
    Integer tryLock(@Param("lockName") String lockName);

    @Select("SELECT RELEASE_LOCK(#{lockName})")
    Integer releaseLock(@Param("lockName") String lockName);
}
