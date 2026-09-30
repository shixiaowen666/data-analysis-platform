package com.bi.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bi.dto.AiBodyKnowledgeDTO;
import com.bi.dto.AiBodyQueryDTO;
import com.bi.dto.AiBodySaveDTO;
import com.bi.dto.AiBodyTableRelDTO;
import com.bi.entity.*;
import com.bi.mapper.*;
import com.bi.service.IAiBodyService;
import com.bi.vo.*;
import com.common.exception.BizException;
import com.metadata.entity.MetaDataSource;
import com.metadata.entity.MetaTable;
import com.metadata.mapper.MetaColumnMapper;
import com.metadata.mapper.MetaDataSourceMapper;
import com.metadata.mapper.MetaTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能体管理 Service 实现
 */
@Service
@RequiredArgsConstructor
public class AiBodyServiceImpl implements IAiBodyService {

    private final AiBodyMapper aiBodyMapper;
    private final AiBodyRelationMapper aiBodyRelationMapper;
    private final AiBodyKnowledgeInfoMapper aiBodyKnowledgeInfoMapper;
    private final MetaDataSourceMapper dataSourceMapper;
    private final MetaTableMapper metaTableMapper;
    private final MetaColumnMapper metaColumnMapper;
    private final OlapTableProMapper olapTableProMapper;
    private final OlapDataModelMapper olapDataModelMapper;

    @Override
    public ApiPageResult<AiBodyVO> listAgents(AiBodyQueryDTO query, Long tenantId) {
        LambdaQueryWrapper<AiBody> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiBody::getTenantId, tenantId)
                .like(StrUtil.isNotBlank(query.getKeyword()), AiBody::getName, query.getKeyword())
                .orderByDesc(AiBody::getUpdatedAt);

        Page<AiBody> page = aiBodyMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        List<AiBodyVO> list = page.getRecords().stream().map(this::toListVO).collect(Collectors.toList());
        return new ApiPageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public AiBodyDetailVO getAgentDetail(String code, Long tenantId) {
        AiBody aiBody = aiBodyMapper.selectOne(
                new LambdaQueryWrapper<AiBody>()
                        .eq(AiBody::getCode, code)
                        .eq(AiBody::getTenantId, tenantId));
        if (aiBody == null) {
            throw new BizException(404, "智能体不存在");
        }

        AiBodyDetailVO vo = new AiBodyDetailVO();
        vo.setId(aiBody.getId());
        vo.setCode(aiBody.getCode());
        vo.setName(aiBody.getName());
        vo.setInteractionMode(aiBody.getInteractionMode());
        vo.setThemeCode(aiBody.getThemeCode());
        vo.setAuthorizeStrategy(aiBody.getAuthorizeStrategy());
        vo.setHotWords(aiBody.getHotWords());
        vo.setDescription(aiBody.getDescription());
        vo.setCreatedBy(aiBody.getCreatedBy());
        vo.setCreatedAt(aiBody.getCreatedAt());
        vo.setUpdatedBy(aiBody.getUpdatedBy());
        vo.setUpdatedAt(aiBody.getUpdatedAt());

        // 加载关联数据表
        List<AiBodyRelation> relations = aiBodyRelationMapper.selectList(
                new LambdaQueryWrapper<AiBodyRelation>()
                        .eq(AiBodyRelation::getCode, code)
                        .eq(AiBodyRelation::getTenantId, tenantId)
                        .orderByAsc(AiBodyRelation::getId));
        vo.setTableRelations(relations.stream().map(this::toTableRelVO).collect(Collectors.toList()));

        // 加载知识库
        List<AiBodyKnowledgeInfo> knowledgeList = aiBodyKnowledgeInfoMapper.selectList(
                new LambdaQueryWrapper<AiBodyKnowledgeInfo>()
                        .eq(AiBodyKnowledgeInfo::getAiBodyId, aiBody.getId())
                        .eq(AiBodyKnowledgeInfo::getTenantId, tenantId)
                        .orderByAsc(AiBodyKnowledgeInfo::getId));
        vo.setKnowledgeList(knowledgeList.stream().map(this::toKnowledgeVO).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IdVO createAgent(AiBodySaveDTO dto, Long tenantId) {
        AiBody aiBody = new AiBody();
        aiBody.setCode(generateCode());
        aiBody.setName(dto.getName().trim());
        aiBody.setDescription(dto.getDescription());
        aiBody.setTenantId(tenantId);
        aiBody.setCreatedBy(0L);
        aiBody.setUpdatedBy(0L);
        aiBodyMapper.insert(aiBody);

        saveRelationsAndKnowledge(aiBody.getCode(), Long.valueOf(aiBody.getId()), dto, tenantId);
        return new IdVO((long) aiBody.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAgent( AiBodySaveDTO dto, Long tenantId) {
        AiBody aiBody = requireAgent(dto.getCode(), tenantId);
        aiBody.setName(dto.getName().trim());
        aiBody.setDescription(dto.getDescription());
        aiBodyMapper.updateById(aiBody);
        // 更新关联表与知识库
        saveRelationsAndKnowledge(dto.getCode(), Long.valueOf(aiBody.getId()), dto, tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAgent(String code, Long tenantId) {
        AiBody aiBody = requireAgent(code, tenantId);

        // 删除关联表
        aiBodyRelationMapper.delete(
                new LambdaQueryWrapper<AiBodyRelation>()
                        .eq(AiBodyRelation::getCode, code)
                        .eq(AiBodyRelation::getTenantId, tenantId));
        // 删除知识库
        aiBodyKnowledgeInfoMapper.delete(
                new LambdaQueryWrapper<AiBodyKnowledgeInfo>()
                        .eq(AiBodyKnowledgeInfo::getAiBodyId, aiBody.getId())
                        .eq(AiBodyKnowledgeInfo::getTenantId, tenantId));
        // 删除智能体
        aiBodyMapper.deleteById(aiBody.getId());
    }

    @Override
    public void deleteRelation(String code, Long id, Long tenantId) {
        if (id == null || id <= 0 || StrUtil.isBlank(code)) {
            return;
        }
        aiBodyRelationMapper.delete(
                new LambdaQueryWrapper<AiBodyRelation>()
                        .eq(AiBodyRelation::getId, id)
                        .eq(AiBodyRelation::getCode, code)
                        .eq(AiBodyRelation::getTenantId, tenantId));
    }

    @Override
    public List<OlapModelRelationVO> availableTables(Long sourceId, String keyword, Long tenantId) {
        MetaDataSource source = dataSourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BizException(404, "数据源不存在");
        }
        List<OlapModelRelationVO> collect = new ArrayList<>();
        LambdaQueryWrapper<OlapDataModel> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OlapDataModel::getSourceId, sourceId)
                .eq(OlapDataModel::getStatus, 1)
                .like(StrUtil.isNotBlank(keyword), OlapDataModel::getName, keyword)
                .orderByAsc(OlapDataModel::getName);
        List<OlapDataModel> olapDataModels = olapDataModelMapper.selectList(wrapper);
        for (OlapDataModel olapDataModel : olapDataModels) {
            OlapModelRelationVO vo = new OlapModelRelationVO();
            vo.setRelationId(olapDataModel.getId());
            vo.setSourceId(sourceId);
            vo.setTableName(olapDataModel.getName());
            vo.setTableComment(olapDataModel.getDescription());
            vo.setRelationType(1);
            collect.add(vo);
        }
        List<Long> factTableIds = olapDataModels.stream().map(OlapDataModel::getFactTableId).collect(Collectors.toList());

        LambdaQueryWrapper<OlapTablePro> wrapper1 = new LambdaQueryWrapper<>();
        wrapper1.eq(OlapTablePro::getSourceId, sourceId)
                .like(StrUtil.isNotBlank(keyword), OlapTablePro::getTbName, keyword)
                .or()
                .like(StrUtil.isNotBlank(keyword), OlapTablePro::getCnName, keyword)
                .orderByAsc(OlapTablePro::getTbName);
        List<OlapModelRelationVO> tables = olapTableProMapper.selectList(wrapper1).stream().filter(table -> !factTableIds.contains(table.getId()))
                .map(this::toCandidateTableVO)
                .collect(Collectors.toList());
        collect.addAll(tables);
        return collect;
    }

    @Override
    public byte[] exportKnowledge(Long aiBodyId, Long tenantId) {
        // TODO: 使用 Apache POI 导出 Excel
        throw new BizException("暂不支持知识库导出");
    }

    // ==================== 私有方法 ====================

    private void saveRelationsAndKnowledge(String code, Long aiBodyId, AiBodySaveDTO dto, Long tenantId) {
        // 保存关联表：增量插入，已存在的跳过
        if (dto.getTableRelations() != null) {
            List<AiBodyRelation> existing = aiBodyRelationMapper.selectList(
                    new LambdaQueryWrapper<AiBodyRelation>()
                            .eq(AiBodyRelation::getCode, code)
                            .eq(AiBodyRelation::getTenantId, tenantId));
            Set<Long> existingIds = existing.stream()
                    .map(AiBodyRelation::getRelationId)
                    .collect(Collectors.toSet());
            for (AiBodyTableRelDTO rel : dto.getTableRelations()) {
                if (existingIds.contains(rel.getRelationId())) {
                    continue;
                }
                AiBodyRelation relation = new AiBodyRelation();
                relation.setCode(code);
                relation.setTenantId(tenantId);
                relation.setRelationId(rel.getRelationId());
                relation.setRelationType(rel.getRelationType() != null ? rel.getRelationType() : 0);
                relation.setCreatedBy(0L);
                relation.setUpdatedBy(0L);
                aiBodyRelationMapper.insert(relation);
            }
        }

        // 保存知识库：先删后插
        aiBodyKnowledgeInfoMapper.delete(
                new LambdaQueryWrapper<AiBodyKnowledgeInfo>()
                        .eq(AiBodyKnowledgeInfo::getAiBodyId, aiBodyId)
                        .eq(AiBodyKnowledgeInfo::getTenantId, tenantId));
        if (dto.getKnowledgeList() != null) {
            for (AiBodyKnowledgeDTO k : dto.getKnowledgeList()) {
                AiBodyKnowledgeInfo info = new AiBodyKnowledgeInfo();
                info.setAiBodyId(aiBodyId);
                info.setTenantId(tenantId);
                info.setKnowledgeElement(k.getKnowledgeElement());
                info.setKnowledgeAlias(k.getKnowledgeAlias());
                info.setCreatedBy(0L);
                info.setUpdatedBy(0L);
                aiBodyKnowledgeInfoMapper.insert(info);
            }
        }
    }

    private AiBody requireAgent(String code, Long tenantId) {
        AiBody aiBody = aiBodyMapper.selectOne(
                new LambdaQueryWrapper<AiBody>()
                        .eq(AiBody::getCode, code)
                        .eq(AiBody::getTenantId, tenantId));
        if (aiBody == null) {
            throw new BizException(404, "智能体不存在");
        }
        return aiBody;
    }

    private String generateCode() {
        return UUID.randomUUID().toString();
    }

    private AiBodyVO toListVO(AiBody entity) {
        AiBodyVO vo = new AiBodyVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setInteractionMode(entity.getInteractionMode());
        vo.setAuthorizeStrategy(entity.getAuthorizeStrategy());
        vo.setThemeCode(entity.getThemeCode());
        vo.setTenantId(entity.getTenantId());
        vo.setCreatedAt(entity.getCreatedAt());
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    private OlapModelRelationVO toTableRelVO(AiBodyRelation entity) {
        OlapModelRelationVO vo = new OlapModelRelationVO();
        vo.setId(entity.getId());
        vo.setRelationId(entity.getRelationId());
        vo.setRelationType(entity.getRelationType());
        if (entity.getRelationType() == 0) {
            OlapTablePro table = olapTableProMapper.selectById(entity.getRelationId());
            if (table != null) {
                vo.setSourceId(table.getSourceId());
                vo.setTableName(table.getTbName());
                vo.setTableComment(table.getCnName());
            }
        } else if (entity.getRelationType() == 1) {
            OlapDataModel model = olapDataModelMapper.selectById(entity.getRelationId());
            if (model != null) {
                vo.setSourceId(model.getSourceId());
                vo.setTableName(model.getName());
                vo.setTableComment(model.getDescription());
            }
        }
        return vo;
    }

    private AiBodyKnowledgeVO toKnowledgeVO(AiBodyKnowledgeInfo entity) {
        AiBodyKnowledgeVO vo = new AiBodyKnowledgeVO();
        vo.setId(entity.getId());
        vo.setAiBodyId(entity.getAiBodyId());
        vo.setKnowledgeElement(entity.getKnowledgeElement());
        vo.setKnowledgeAlias(entity.getKnowledgeAlias());
        vo.setCreatedAt(entity.getCreatedAt());
        return vo;
    }

    private OlapModelRelationVO toCandidateTableVO(OlapTablePro table) {
        OlapModelRelationVO vo = new OlapModelRelationVO();
        vo.setRelationId(table.getId());
        vo.setSourceId(table.getSourceId());
        vo.setTableName(table.getTbName());
        vo.setTableComment(table.getCnName());
        vo.setRelationType(0);
        return vo;
    }
}
