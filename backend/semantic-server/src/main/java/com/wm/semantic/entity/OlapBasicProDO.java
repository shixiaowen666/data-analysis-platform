package com.wm.semantic.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 指标维度基础信息表
 */
@Data
@TableName("olap_basic_pro")
public class OlapBasicProDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String keyStr;
    private String chineseName;
    private String classificationLabel;
    private String classificationLabelName;
    private String standardName;
    private String alias;
    private String englishName;
    private String abbreviation;
    private Integer category;
    private Integer status;
    private Long principal;
    private String principalName;
    private String principalEmail;
    private Long approver;
    private String approverName;
    private String approverEmail;
    private String isShow;
    private String olapLabel;
    private String olapLabelName;
    private String dataType;
    private Integer dataSourceType;
    private String createdBy;
    private Date createdAt;
    private String updatedBy;
    private Date updatedAt;
}
