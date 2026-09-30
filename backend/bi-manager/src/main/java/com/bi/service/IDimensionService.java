package com.bi.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bi.dto.DimensionQueryDTO;
import com.bi.dto.DimensionReq;
import com.bi.vo.DimensionDetailVO;
import com.bi.vo.DimensionVO;
import com.bi.vo.NameCheckVO;

import java.util.List;

/**
 * 维度管理 Service
 */
public interface IDimensionService {

    /**
     * 分页查询维度列表
     */
    IPage<DimensionVO> listDimensions(DimensionQueryDTO query, Long tenantId);

    /**
     * 获取维度详情
     */
    DimensionDetailVO getDimensionDetail(Long id);

    /**
     * 新建维度
     */
    void createDimension(DimensionReq req, Long tenantId);

    /**
     * 编辑维度
     */
    void updateDimension(DimensionReq req, Long tenantId);

    /**
     * 获取维度编码（自动生成唯一编码）
     */
    String generateDimensionCode();

    /**
     * 下线维度
     */
    void offlineDimension(Long id, Long tenantId);

    /**
     * 删除维度
     */
    void deleteDimension(Long id, Long tenantId);

    /**
     * 上线维度
     */
    void onlineDimension(Long id, Long tenantId);

    /**
     * 查询所有已上线的维度列表（不分页）
     */
    List<DimensionVO> listAllDimensions(String keyword, Long tenantId);

    /**
     * 获取维值采集列表
     */
    List<String> getDimensionValues(Long id);

    /**
     * 检查维度是否被计算指标引用
     */
    List<DimensionVO> checkReferencedByCalculation(String code);

    /**
     * 校验英文名是否已存在
     */
    NameCheckVO checkEnglishName(String name, Long excludeId, Long tenantId);

    /**
     * 校验中文名是否已存在
     */
    NameCheckVO checkChineseName(String name, Long excludeId, Long tenantId);
}
