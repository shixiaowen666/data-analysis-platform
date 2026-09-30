package com.metadata.engine.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.common.exception.BizException;
import com.metadata.dto.meta.MetaCollectRequestDTO;
import com.metadata.engine.MetadataCollectEngine;
import com.metadata.engine.enums.CollectStatusEnum;
import com.metadata.engine.enums.CollectTypeEnum;
import com.metadata.engine.factory.MetadataEngineFactory;
import com.metadata.engine.lock.DatabaseCollectLock;
import com.metadata.engine.model.CollectContext;
import com.metadata.engine.model.CollectRequest;
import com.metadata.engine.model.CollectResult;
import com.metadata.engine.support.CollectLogWriter;
import com.metadata.entity.MetaCollectLog;
import com.metadata.entity.MetaDataSource;
import com.metadata.service.MetaCollectLogService;
import com.metadata.service.MetaDataSourceService;
import com.metadata.service.MetaSelectTableService;
import com.metadata.vo.meta.MetaCollectStartVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 元数据采集编排服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataCollectOrchestrator {

    private final MetaDataSourceService metaDataSourceService;
    private final MetaCollectLogService metaCollectLogService;
    private final MetaSelectTableService metaSelectTableService;
    private final MetadataEngineFactory metadataEngineFactory;
    private final MetadataCollectExecutor metadataCollectExecutor;
    private final DatabaseCollectLock databaseCollectLock;
    private final ObjectMapper objectMapper;

    public MetaCollectStartVO startCollect(Long sourceId, MetaCollectRequestDTO requestDto) {
        MetaDataSource dataSource = metaDataSourceService.getById(sourceId);
        if (dataSource == null) {
            throw new BizException("数据源不存在");
        }
        CollectRequest request = buildRequest(sourceId, dataSource, requestDto);

        Long collectLogId = databaseCollectLock.executeWithLock(sourceId, () -> {
            assertNoRunningTask(sourceId);
            MetaCollectLog collectLog = createRunningLog(dataSource, request);
            metaCollectLogService.save(collectLog);

            CollectContext context = buildContext(dataSource, collectLog, request);
            metadataCollectExecutor.execute(() -> runCollect(context));
            return collectLog.getId();
        });

        MetaCollectStartVO vo = new MetaCollectStartVO();
        vo.setCollectLogId(collectLogId);
        vo.setSourceId(sourceId);
        vo.setStatus(CollectStatusEnum.RUNNING.getCode());
        vo.setMessage("采集任务进行中");
        return vo;
    }

    private void runCollect(CollectContext context) {
        Long collectLogId = context.getCollectLog().getId();
        try {
            MetadataCollectEngine engine = metadataEngineFactory.getEngine(context.getDataSource().getDbType());
            CollectResult result = engine.collect(context);
            finalizeCollectLog(collectLogId, context.getLogWriter(), result);
        } catch (Exception ex) {
            log.error("采集任务执行异常, collectLogId={}", collectLogId, ex);
            context.getLogWriter().error("采集失败：" + ex.getMessage());
            CollectResult result = new CollectResult();
            result.setSuccess(false);
            result.setErrorMessage(ex.getMessage());
            finalizeCollectLog(collectLogId, context.getLogWriter(), result);
        }
    }

    private void finalizeCollectLog(Long collectLogId, CollectLogWriter logWriter, CollectResult result) {
        MetaCollectLog collectLog = metaCollectLogService.getById(collectLogId);
        if (collectLog == null) {
            return;
        }
        collectLog.setFinishedAt(LocalDateTime.now());
        collectLog.setTableCount(result.getNewTableCount());
        collectLog.setColumnCount(result.getNewColumnCount());
        collectLog.setUpdatedTableCount(result.getUpdatedTableCount());
        collectLog.setUpdatedColumnCount(result.getUpdatedColumnCount());
        collectLog.setDeletedTableCount(result.getDeletedTableCount());
        collectLog.setDeletedColumnCount(result.getDeletedColumnCount());
        collectLog.setCollectLog(logWriter.toJson());
        if (result.isSuccess()) {
            collectLog.setErrorMsg(null);
        } else {
            collectLog.setErrorMsg(resolveFailureMessage(result, logWriter));
        }
        collectLog.setStatus(result.isSuccess()
                ? CollectStatusEnum.SUCCESS.getCode()
                : CollectStatusEnum.FAIL.getCode());
        metaCollectLogService.updateById(collectLog);
    }

    private String resolveFailureMessage(CollectResult result, CollectLogWriter logWriter) {
        if (StringUtils.isNotBlank(result.getErrorMessage())) {
            return result.getErrorMessage();
        }
        return logWriter.resolveFailureMessage();
    }

    private CollectContext buildContext(MetaDataSource dataSource,
                                        MetaCollectLog collectLog,
                                        CollectRequest request) {
        CollectContext context = new CollectContext();
        context.setDataSource(dataSource);
        context.setCollectLog(collectLog);
        context.setRequest(request);
        context.setLogWriter(new CollectLogWriter(objectMapper));
        return context;
    }

    private MetaCollectLog createRunningLog(MetaDataSource dataSource, CollectRequest request) {
        MetaCollectLog collectLog = new MetaCollectLog();
        collectLog.setSourceId(dataSource.getId());
        collectLog.setTenantId(dataSource.getTenantId());
        collectLog.setCollectType(request.getCollectType().getCode());
        collectLog.setStatus(CollectStatusEnum.RUNNING.getCode());
        collectLog.setStartedAt(LocalDateTime.now());
        if (request.isSelectCollect()) {
            collectLog.setTableNames(String.join(",", request.getTableNames()));
        }
        return collectLog;
    }

    private void assertNoRunningTask(Long sourceId) {
        long runningCount = metaCollectLogService.count(new LambdaQueryWrapper<MetaCollectLog>()
                .eq(MetaCollectLog::getSourceId, sourceId)
                .eq(MetaCollectLog::getStatus, CollectStatusEnum.RUNNING.getCode()));
        if (runningCount > 0) {
            throw new BizException("该数据源已有采集任务进行中");
        }
    }

    private CollectRequest buildRequest(Long sourceId,
                                        MetaDataSource dataSource,
                                        MetaCollectRequestDTO requestDto) {
        if (requestDto == null) {
            requestDto = new MetaCollectRequestDTO();
            requestDto.setCollectType(CollectTypeEnum.FULL.getCode());
        }
        if (StringUtils.isBlank(requestDto.getCollectType())) {
            requestDto.setCollectType(CollectTypeEnum.FULL.getCode());
        }
        CollectTypeEnum collectType;
        try {
            collectType = CollectTypeEnum.ofCode(requestDto.getCollectType());
        } catch (IllegalArgumentException ex) {
            throw new BizException(ex.getMessage());
        }
        CollectRequest request = new CollectRequest();
        request.setCollectType(collectType);
        if (collectType == CollectTypeEnum.SELECT) {
            request.setTableNames(resolveSelectTableNames(sourceId, dataSource, requestDto));
        }
        return request;
    }

    /**
     * 选表采集：优先使用本次请求携带的表名并持久化，否则读取已保存的选中表，仅采集这些表。
     */
    private List<String> resolveSelectTableNames(Long sourceId,
                                                 MetaDataSource dataSource,
                                                 MetaCollectRequestDTO requestDto) {
        List<String> fromRequest = normalizeTableNames(requestDto.getTableNames());
        if (!fromRequest.isEmpty()) {
            metaSelectTableService.saveSelected(sourceId.intValue(), fromRequest, dataSource.getTenantId());
            return fromRequest;
        }
        List<String> saved = metaSelectTableService.listSelected(sourceId.intValue()).getTableNames();
        if (saved == null || saved.isEmpty()) {
            throw new BizException("选表采集至少选择一张表");
        }
        return saved;
    }

    private List<String> normalizeTableNames(List<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return new ArrayList<>();
        }
        return tableNames.stream()
                .filter(StringUtils::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }
}
