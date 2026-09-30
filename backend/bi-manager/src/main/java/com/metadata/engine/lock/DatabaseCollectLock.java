package com.metadata.engine.lock;

import com.common.exception.BizException;
import com.metadata.mapper.CollectLockMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * 基于 MySQL GET_LOCK 的数据源采集锁（每个数据源一把锁）
 */
@Component
@RequiredArgsConstructor
public class DatabaseCollectLock {

    private static final String LOCK_PREFIX = "meta_collect_source_";

    private final CollectLockMapper collectLockMapper;

    public <T> T executeWithLock(Long sourceId, Supplier<T> action) {
        String lockName = LOCK_PREFIX + sourceId;
        Integer locked = collectLockMapper.tryLock(lockName);
        if (locked == null || locked != 1) {
            throw new BizException("该数据源正在采集，请稍后重试");
        }
        try {
            return action.get();
        } finally {
            collectLockMapper.releaseLock(lockName);
        }
    }
}
