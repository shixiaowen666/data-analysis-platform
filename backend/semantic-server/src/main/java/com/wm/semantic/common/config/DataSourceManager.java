package com.wm.semantic.common.config;

import com.alibaba.druid.pool.DruidDataSource;
import com.wm.semantic.entity.MetaDataSourceDO;
import com.wm.semantic.mapper.MetaDataSourceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 meta_data_source.id 动态创建并缓存数据源连接池。
 */
@Slf4j
@Component
public class DataSourceManager {

    @Resource
    private MetaDataSourceMapper metaDataSourceMapper;

    private final Map<Long, DataSource> cache = new ConcurrentHashMap<>();

    public DataSource get(Long sourceId) {
        if (sourceId == null) return null;
        return cache.computeIfAbsent(sourceId, this::createDataSource);
    }

    private DataSource createDataSource(Long sourceId) {
        MetaDataSourceDO meta = metaDataSourceMapper.selectById(sourceId);
        if (meta == null) {
            log.warn("[DS] meta_data_source 未找到, sourceId={}", sourceId);
            return null;
        }
        String url = meta.getJdbcUrl();
        if (url == null || url.trim().isEmpty()) {
            // 拼装 JDBC URL
            url = "jdbc:" + meta.getDbType().toLowerCase() + "://" + meta.getHost() + ":" + meta.getPort() + "/" + meta.getDefaultDb();
        }
        DruidDataSource ds = new DruidDataSource();
        ds.setUrl(url);
        ds.setUsername(meta.getUsername());
        ds.setPassword(meta.getPassword());
        ds.setInitialSize(2);
        ds.setMinIdle(2);
        ds.setMaxActive(10);
        ds.setTestWhileIdle(true);
        log.info("[DS] 已创建数据源: sourceId={}, url={}", sourceId, url);
        return ds;
    }
}
