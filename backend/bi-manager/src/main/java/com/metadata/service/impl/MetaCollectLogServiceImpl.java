package com.metadata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.common.exception.BizException;
import com.common.result.PageResult;
import com.metadata.dto.meta.MetaCollectLogQueryDTO;
import com.metadata.entity.MetaCollectLog;
import com.metadata.mapper.MetaCollectLogMapper;
import com.metadata.service.MetaCollectLogService;
import com.common.util.BeanUtil;
import com.metadata.vo.meta.CollectLogEntryVO;
import com.metadata.vo.meta.MetaCollectLogVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 采集日志 Service 实现
 */
@Service
public class MetaCollectLogServiceImpl extends ServiceImpl<MetaCollectLogMapper, MetaCollectLog>
        implements MetaCollectLogService {

    private final ObjectMapper objectMapper;

    public MetaCollectLogServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<MetaCollectLogVO> pageList(MetaCollectLogQueryDTO query) {
        MetaCollectLogQueryDTO params = query != null ? query : new MetaCollectLogQueryDTO();
        if (params.getSourceId() == null) {
            throw new BizException("数据源 ID 不能为空");
        }

        int pageNo = params.getPage() != null && params.getPage() > 0 ? params.getPage() : 1;
        int pageSize = params.getPageSize() != null && params.getPageSize() > 0 ? params.getPageSize() : 10;

        LambdaQueryWrapper<MetaCollectLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetaCollectLog::getSourceId, params.getSourceId())
                .eq(StringUtils.isNotBlank(params.getStatus()), MetaCollectLog::getStatus, params.getStatus())
                .orderByDesc(MetaCollectLog::getUpdatedAt);

        Page<MetaCollectLog> page = page(new Page<>(pageNo, pageSize), wrapper);
        List<MetaCollectLogVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(page.getTotal(), records, Long.valueOf(pageNo), Long.valueOf(pageSize));
    }

    @Override
    public MetaCollectLogVO getDetail(Long id) {
        MetaCollectLog entity = getById(id);
        if (entity == null) {
            throw new BizException("采集日志不存在");
        }
        MetaCollectLogVO vo = toVO(entity);
        vo.setLogLines(parseLogLines(resolveLogContent(entity)));
        return vo;
    }

    private String resolveLogContent(MetaCollectLog entity) {
        if (StringUtils.isNotBlank(entity.getCollectLog())) {
            return entity.getCollectLog();
        }
        // 兼容旧数据：过程日志曾写入 error_msg
        if (StringUtils.isNotBlank(entity.getErrorMsg()) && entity.getErrorMsg().trim().startsWith("[")) {
            return entity.getErrorMsg();
        }
        return null;
    }

    private MetaCollectLogVO toVO(MetaCollectLog entity) {
        MetaCollectLogVO vo = new MetaCollectLogVO();
        BeanUtil.copy(entity, vo);
        vo.setCollectTypeLabel(resolveCollectTypeLabel(entity.getCollectType()));
        vo.setDiffSummary(buildDiffSummary(entity));
        vo.setDurationSeconds(calculateDuration(entity));
        return vo;
    }

    private List<CollectLogEntryVO> parseLogLines(String errorMsg) {
        if (StringUtils.isBlank(errorMsg) || !errorMsg.trim().startsWith("[")) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(errorMsg, new TypeReference<List<CollectLogEntryVO>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private String resolveCollectTypeLabel(String collectType) {
        if ("select".equalsIgnoreCase(collectType)) {
            return "选表采集";
        }
        return "全库采集";
    }

    private String buildDiffSummary(MetaCollectLog entity) {
        int newTables = entity.getTableCount() != null ? entity.getTableCount() : 0;
        int updatedTables = entity.getUpdatedTableCount() != null ? entity.getUpdatedTableCount() : 0;
        int newColumns = entity.getColumnCount() != null ? entity.getColumnCount() : 0;
        int updatedColumns = entity.getUpdatedColumnCount() != null ? entity.getUpdatedColumnCount() : 0;
        int deletedTables = entity.getDeletedTableCount() != null ? entity.getDeletedTableCount() : 0;
        int deletedColumns = entity.getDeletedColumnCount() != null ? entity.getDeletedColumnCount() : 0;
        return newTables + "/" + updatedTables + "/" + newColumns + "/"
                + updatedColumns + "/" + deletedTables + "/" + deletedColumns;
    }

    private Long calculateDuration(MetaCollectLog entity) {
        if (entity.getStartedAt() == null || entity.getFinishedAt() == null) {
            return null;
        }
        return Duration.between(entity.getStartedAt(), entity.getFinishedAt()).getSeconds();
    }
}
