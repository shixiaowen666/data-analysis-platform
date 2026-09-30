package com.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.exception.BizException;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaDataSourceQueryDTO;
import com.metadata.dto.meta.MetaDataSourceSaveDTO;
import com.metadata.entity.MetaCollectLog;
import com.metadata.entity.MetaColumn;
import com.metadata.entity.MetaDataSource;
import com.metadata.entity.MetaSelectTable;
import com.metadata.entity.MetaTable;
import com.metadata.mapper.MetaDataSourceMapper;
import com.metadata.service.MetaCollectLogService;
import com.metadata.service.MetaColumnService;
import com.metadata.service.MetaDataSourceService;
import com.metadata.service.MetaSelectTableService;
import com.metadata.service.MetaTableService;
import com.common.util.BeanUtil;
import com.common.util.CryptoUtils;
import com.common.util.JdbcConnectionTester;
import com.common.util.JdbcUrlBuilder;
import com.metadata.vo.meta.DataSourceTestResultVO;
import com.metadata.vo.meta.MetaDataSourceVO;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 数据源 Service 实现
 */
@Service
public class MetaDataSourceServiceImpl extends ServiceImpl<MetaDataSourceMapper, MetaDataSource>
        implements MetaDataSourceService {

    private static final Long DEFAULT_TENANT_ID = 1L;

    private final MetaTableService metaTableService;
    private final MetaColumnService metaColumnService;
    private final MetaCollectLogService metaCollectLogService;
    private final MetaSelectTableService metaSelectTableService;
    private final CryptoUtils cryptoUtils;

    public MetaDataSourceServiceImpl(@Lazy MetaTableService metaTableService,
                                     @Lazy MetaColumnService metaColumnService,
                                     @Lazy MetaCollectLogService metaCollectLogService,
                                     @Lazy MetaSelectTableService metaSelectTableService,
                                     CryptoUtils cryptoUtils) {
        this.metaTableService = metaTableService;
        this.metaColumnService = metaColumnService;
        this.metaCollectLogService = metaCollectLogService;
        this.metaSelectTableService = metaSelectTableService;
        this.cryptoUtils = cryptoUtils;
    }

    @Override
    public PageResult<MetaDataSourceVO> pageList(MetaDataSourceQueryDTO query) {
        MetaDataSourceQueryDTO params = query != null ? query : new MetaDataSourceQueryDTO();
        int pageNum = resolvePage(params);
        int pageSize = params.getPageSize() != null && params.getPageSize() > 0 ? params.getPageSize() : 10;
        String nameKeyword = resolveNameKeyword(params);

        LambdaQueryWrapper<MetaDataSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(nameKeyword), MetaDataSource::getName, nameKeyword)
                .eq(StringUtils.isNotBlank(params.getDbType()), MetaDataSource::getDbType, params.getDbType())
                .eq(params.getStatus() != null, MetaDataSource::getStatus, params.getStatus())
                .eq(params.getTenantId() != null, MetaDataSource::getTenantId, params.getTenantId())
                .orderByDesc(MetaDataSource::getUpdatedAt);

        Page<MetaDataSource> page = page(new Page<>(pageNum, pageSize), wrapper);
        List<MetaDataSourceVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), records, (long) pageNum, (long) pageSize);
    }

    private int resolvePage(MetaDataSourceQueryDTO params) {
        if (params.getPage() != null && params.getPage() > 0) {
            return params.getPage();
        }
        return 1;
    }

    private String resolveNameKeyword(MetaDataSourceQueryDTO params) {
        if (StringUtils.isNotBlank(params.getKeyword())) {
            return params.getKeyword().trim();
        }
        if (StringUtils.isNotBlank(params.getName())) {
            return params.getName().trim();
        }
        return null;
    }

    @Override
    public MetaDataSourceVO getDetail(Long id) {
        MetaDataSource entity = getByIdOrThrow(id);
        return toVO(entity);
    }

    @Override
    public Long create(MetaDataSourceSaveDTO dto) {
        validateCreate(dto);
        MetaDataSource entity = buildEntity(dto, null);
        entity.setStatus(1);
        entity.setTenantId(resolveTenantId(dto.getTenantId()));
        save(entity);
        return entity.getId();
    }

    @Override
    public void updateDataSource(MetaDataSourceSaveDTO dto) {
        if (dto.getId() == null) {
            throw new BizException("数据源 ID 不能为空");
        }
        MetaDataSource existing = getByIdOrThrow(dto.getId());
        fillFromExistingIfBlank(dto, existing);
        validateUpdate(dto);
        MetaDataSource entity = buildEntity(dto, existing);
        entity.setId(existing.getId());
        entity.setTenantId(existing.getTenantId());
        entity.setStatus(existing.getStatus());
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDataSource(Long id) {
        getByIdOrThrow(id);
        List<MetaTable> tables = metaTableService.list(new LambdaQueryWrapper<MetaTable>()
                .eq(MetaTable::getSourceId, id));
        if (!tables.isEmpty()) {
            List<Long> tableIds = tables.stream().map(MetaTable::getId).collect(Collectors.toList());
            metaColumnService.remove(new LambdaQueryWrapper<MetaColumn>()
                    .in(MetaColumn::getTableId, tableIds));
        }
        metaTableService.remove(new LambdaQueryWrapper<MetaTable>()
                .eq(MetaTable::getSourceId, id));
        metaCollectLogService.remove(new LambdaQueryWrapper<MetaCollectLog>()
                .eq(MetaCollectLog::getSourceId, id));
        metaSelectTableService.remove(new LambdaQueryWrapper<MetaSelectTable>()
                .eq(MetaSelectTable::getDatasourceId, id.intValue()));
        removeById(id);
    }

    @Override
    public DataSourceTestResultVO testConnection(MetaDataSourceSaveDTO dto) {
        MetaDataSource existing = null;
        if (dto.getId() != null) {
            existing = getByIdOrThrow(dto.getId());
            fillFromExistingIfBlank(dto, existing);
        }
        validateTestConnection(dto);
        String jdbcUrl = resolveJdbcUrl(dto);
        String password = resolvePassword(dto, existing);
        return JdbcConnectionTester.test(dto.getDbType(), jdbcUrl, dto.getUsername(), password, dto.getSchemaName());
    }

    @Override
    public String previewJdbcUrl(MetaDataSourceSaveDTO dto) {
        validateJdbcPreview(dto);
        return resolveJdbcUrl(dto);
    }

    private MetaDataSource getByIdOrThrow(Long id) {
        MetaDataSource entity = getById(id);
        if (entity == null) {
            throw new BizException("数据源不存在");
        }
        return entity;
    }

    private void validateCreate(MetaDataSourceSaveDTO dto) {
        validateConnectionFields(dto);
        if (shouldKeepExistingPassword(dto.getPassword())) {
            throw new BizException("密码不能为空");
        }
    }

    private void validateUpdate(MetaDataSourceSaveDTO dto) {
        validateConnectionFields(dto);
        if (StringUtils.isBlank(dto.getName())) {
            throw new BizException("编辑数据源缺少必要参数");
        }
    }

    private void validateConnectionFields(MetaDataSourceSaveDTO dto) {
        if (StringUtils.isAnyBlank(dto.getDbType(), dto.getHost(), dto.getUsername(), dto.getDefaultDb())
                || dto.getPort() == null) {
            throw new BizException("数据源连接信息不完整");
        }
    }

    private void validateTestConnection(MetaDataSourceSaveDTO dto) {
        if (StringUtils.isAnyBlank(dto.getDbType(), dto.getHost(), dto.getUsername(), dto.getDefaultDb())
                || dto.getPort() == null) {
            throw new BizException("测试连接缺少必要参数");
        }
        if (dto.getId() == null && shouldKeepExistingPassword(dto.getPassword())) {
            throw new BizException("新建数据源测试连接时密码不能为空");
        }
    }

    private void validateJdbcPreview(MetaDataSourceSaveDTO dto) {
        if (StringUtils.isAnyBlank(dto.getDbType(), dto.getHost(), dto.getDefaultDb()) || dto.getPort() == null) {
            throw new BizException("生成 JDBC URL 缺少必要参数");
        }
    }

    private MetaDataSource buildEntity(MetaDataSourceSaveDTO dto, MetaDataSource existing) {
        MetaDataSource entity = new MetaDataSource();
        entity.setName(StringUtils.trim(dto.getName()));
        entity.setDbType(StringUtils.trim(dto.getDbType()));
        entity.setHost(StringUtils.trim(dto.getHost()));
        entity.setPort(dto.getPort());
        entity.setDefaultDb(StringUtils.trim(dto.getDefaultDb()));
        entity.setSchemaName(StringUtils.trimToNull(dto.getSchemaName()));
        entity.setUsername(StringUtils.trim(dto.getUsername()));
        entity.setJdbcUrl(buildJdbcUrlForSave(dto));
        entity.setPassword(resolvePassword(dto, existing));
        return entity;
    }

    /**
     * 保存时始终根据页面提交的连接信息重新生成 JDBC URL，忽略前端回传的 jdbcUrl。
     */
    private String buildJdbcUrlForSave(MetaDataSourceSaveDTO dto) {
        return JdbcUrlBuilder.build(dto.getDbType(), dto.getHost(), dto.getPort(),
                dto.getDefaultDb(), dto.getSchemaName());
    }

    private String resolveJdbcUrl(MetaDataSourceSaveDTO dto) {
        return JdbcUrlBuilder.resolve(dto.getDbType(), dto.getHost(), dto.getPort(),
                dto.getDefaultDb(), dto.getSchemaName(), dto.getJdbcUrl());
    }

    private String resolvePassword(MetaDataSourceSaveDTO dto, MetaDataSource existing) {
        if (!shouldKeepExistingPassword(dto.getPassword())) {
            try {
                return cryptoUtils.decryptPassword(dto.getPassword());
            } catch (Exception e) {
                throw new BizException("密码解密失败: " + e.getMessage());
            }
        }
        if (existing != null && StringUtils.isNotBlank(existing.getPassword())) {
            return existing.getPassword();
        }
        throw new BizException("密码不能为空");
    }

    /**
     * 未传密码、传空串，或传前端掩码占位符时，保留库中密码。
     */
    private boolean shouldKeepExistingPassword(String password) {
        if (StringUtils.isBlank(password)) {
            return true;
        }
        String trimmed = password.trim();
        return trimmed.matches("^[*●•]+$");
    }

    private Long resolveTenantId(Long tenantId) {
        return tenantId != null ? tenantId : DEFAULT_TENANT_ID;
    }

    private void fillFromExistingIfBlank(MetaDataSourceSaveDTO dto, MetaDataSource existing) {
        if (StringUtils.isBlank(dto.getName())) {
            dto.setName(existing.getName());
        }
        if (StringUtils.isBlank(dto.getDbType())) {
            dto.setDbType(existing.getDbType());
        }
        if (StringUtils.isBlank(dto.getHost())) {
            dto.setHost(existing.getHost());
        }
        if (dto.getPort() == null) {
            dto.setPort(existing.getPort());
        }
        if (StringUtils.isBlank(dto.getDefaultDb())) {
            dto.setDefaultDb(existing.getDefaultDb());
        }
        if (StringUtils.isBlank(dto.getSchemaName())) {
            dto.setSchemaName(existing.getSchemaName());
        }
        if (StringUtils.isBlank(dto.getUsername())) {
            dto.setUsername(existing.getUsername());
        }
    }

    private MetaDataSourceVO toVO(MetaDataSource entity) {
        MetaDataSourceVO vo = new MetaDataSourceVO();
        BeanUtil.copy(entity, vo);
        vo.setStatusName(entity.getStatus() != null && entity.getStatus() == 1 ? "已启用" : "已停用");
        vo.setDisplayDbSchema(buildDisplayDbSchema(entity.getDefaultDb(), entity.getSchemaName()));
        return vo;
    }

    private String buildDisplayDbSchema(String defaultDb, String schemaName) {
        if (StringUtils.isBlank(schemaName)) {
            return StringUtils.defaultString(defaultDb, "");
        }
        if (StringUtils.isBlank(defaultDb)) {
            return schemaName;
        }
        return defaultDb + " / " + schemaName;
    }
}
