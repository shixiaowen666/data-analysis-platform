package com.bi.service;

import com.bi.dto.*;
import com.bi.vo.*;

import java.util.List;

/**
 * 字段映射管理 Service
 */
public interface IFieldMappingService {

    /**
     * F1: 分析表列表
     */
    ApiPageResult<OlapTableVO> listTables(OlapTableQueryDTO query, Long tenantId);

    /**
     * 分析表详情（含字段映射列表）
     */
    OlapTableDetailVO getTableDetail(Long tableId, Long tenantId);

    /**
     * F2: 查询未注册表列表
     */
    List<UnregisteredTableVO> listUnregisteredTables(Long sourceId, Long tenantId);

    /**
     * F2: 注册物理表
     */
    Long registerPhysicalTable(PhysicalTableRegisterDTO dto, Long tenantId);

    /**
     * F3: 注册视图
     */
    Long registerView(ViewRegisterDTO dto, Long tenantId);

    /**
     * 执行 SQL 解析字段
     */
    List<ParseColumnVO> executeParse(FieldExecuteParseDTO dto);

    /**
     * F4: 编辑视图 - 查询回显
     */
    ViewInfoVO getViewInfo(Long tableId, Long tenantId);

    /**
     * F4: 编辑视图 - 保存修改
     */
    void saveViewEdit(ViewEditDTO dto, Long tenantId);

    /**
     * F5: 语义映射 - 获取表的字段映射列表
     */
    List<FieldMappingVO> getFieldMappings(Long tableId, Long tenantId);

    /**
     * F5: 语义映射 - 批量保存字段映射
     */
    void saveSemanticMappings(SemanticMappingSaveDTO dto, Long tenantId);

    /**
     * F6/F7: 批量注册维度/指标
     */
    void batchRegister(BatchRegisterDTO dto, Long tenantId);

    /**
     * 删除分析表
     */
    void deleteTable(Long tableId, Long tenantId);

    /**
     * 获取所有数据源选项
     */
    List<DataSourceOptionVO> listDataSources();

    List<TableMappingVo> listTablesBySourceId(Long sourceId,String keyword, Long currentTenantId);

    DimensionVO ptdate();
}
