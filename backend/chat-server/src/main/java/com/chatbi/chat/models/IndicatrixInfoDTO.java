package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 单个指标信息
 *
 * @Author: gaowenqing
 * @Date: 2020/2/7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatrixInfoDTO implements Serializable {

    @Schema(description = "指标id")
    private Long id;

    @Schema(description = "业务主题")
    private String businessTheme;

    @Schema(description = "业务主题名称")
    private String businessThemeName;

    @Schema(description = "业务线")
    private String businessLine;

    @Schema(description = "业务线名称")
    private String businessLineName;

    @Schema(description = "时间周期")
    private String timePeriod;

    @Schema(description = "时间周期名称")
    private String timePeriodName;

    @Schema(description = "是否显示")
    @NotNull(message = "是否显示不能为空")
    private String isShow;

    @Schema(description = "是否验证")
    private String checked;

    @Schema(description = "认证类型（0 平台认证, 1 部分认证）")
    private Integer approveType;

    @Schema(description = "词根")
    private String businessRoot;

    @Schema(description = "词根名称")
    private String businessRootName;

//    @Schema(description = "修饰词")
//    private List<DecorateWordDTO> decorateWord;

    @Schema(description = "标准名称")
    private String standardName;

    @Schema(description = "别名")
    private String alias;

    @Schema(description = "英文名称")
    private String englishName;

    @Schema(description = "分类标签")
    private String classificationLabel;

    @Schema(description = "分类标签名称")
    private String classificationLabelName;

    @Schema(description = "olap标签")
    private String olapLabel;

    @Schema(description = "olap标签名称")
    private String olapLabelName;

    @Schema(description = "口径描述")
    private String caliberDescription;

//    @Schema(description = "原生生产")
//    private List<NativeProductionDTO> nativeProduction;

    @Schema(description = "衍生类型 0无 1是基础 2 高级配置")
    private Integer derivativeType;

//    @Schema(description = "衍生生产")
//    private DerivativeProductionDTO derivativeProduction;
//
//    @Schema(description = "计算生产")
//    private CalculatedProductionDTO calculatedProduction;

    @Schema(description = "权限")
    private Integer authority;

    @Schema(description = "权限名称")
    private String authorityName;

    @Schema(description = "状态 0:删除 1:审核中 2:已上线 3:已下线")
    private Integer status;

    @Schema(description = "状态名称")
    private String statusName;

    @Schema(description = "负责人用户id")
    private Long principal;

    @Schema(description = "负责人名字")
    private String principalName;

    @Schema(description = "负责人邮箱")
    private String principalEmail;

//    @Schema(description = "审批人")
//    private ApproverDTO approver;

    @Schema(description = "更新时间")
    private Date modifyTime;

    @Schema(description = "指标监控级别")
    private String monitorLevel;

    @Schema(description = "权限分组")
    private String oauthGroup;

    @Schema(description = "预警信息")
    private String warningInfo;

    @Schema(description = "工作流id")
    private Long workflowId;

    @Schema(description = "字段数据类型")
    private String dataType;

    @Schema(description = "租户id")
    private Long tenantId;

    @Schema(description = "报表目录ids")
    private String catalogIds;

    private List<Long> catalogIdList;

}
