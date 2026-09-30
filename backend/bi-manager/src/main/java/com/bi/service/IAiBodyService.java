package com.bi.service;

import com.bi.dto.AiBodyQueryDTO;
import com.bi.dto.AiBodySaveDTO;
import com.bi.vo.*;

import java.util.List;

/**
 * 智能体管理 Service
 */
public interface IAiBodyService {

    /**
     * 智能体分页列表
     */
    ApiPageResult<AiBodyVO> listAgents(AiBodyQueryDTO query, Long tenantId);

    /**
     * 智能体详情
     */
    AiBodyDetailVO getAgentDetail(String code, Long tenantId);

    /**
     * 创建智能体
     */
    IdVO createAgent(AiBodySaveDTO dto, Long tenantId);

    /**
     * 更新智能体
     */
    void updateAgent( AiBodySaveDTO dto, Long tenantId);

    /**
     * 删除智能体
     */
    void deleteAgent(String code, Long tenantId);

    /**
     * 获取数据源下的可用表列表
     */
    List<OlapModelRelationVO> availableTables(Long sourceId, String keyword, Long tenantId);

    /**
     * 删除智能体关联表
     */
    void deleteRelation(String code, Long id, Long tenantId);

    /**
     * 知识库导出为 Excel
     */
    byte[] exportKnowledge(Long aiBodyId, Long tenantId);
}
