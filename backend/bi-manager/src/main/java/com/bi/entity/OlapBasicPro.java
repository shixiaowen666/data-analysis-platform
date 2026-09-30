package com.bi.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 指标维度基础信息表
 */
@Data
@TableName("olap_basic_pro")
public class OlapBasicPro implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    /**
     * 1-维度 2-指标
     */
    private Integer category;

    /**
     * 唯一 key 用于校验名称重复
     */
    private String keyStr;

    private String chineseName;

    private String standardName;

    private String alias;

    private String englishName;

    private String abbreviation;

    private String classificationLabel;

    private String classificationLabelName;

    private String olapLabel;

    private String olapLabelName;

    private String dataType;

    /**
     * 1-公司 -1-个人
     */
    private Integer dataSourceType;

    /**
     * 0-草稿 1-审批中 2-已上线 3-已下线
     */
    private Integer status;

    private Long principal;

    private String principalName;

    private String principalEmail;

    private Long approver;

    private String approverName;

    private String approverEmail;

    /**
     * 是否显示 1-是 0-否
     */
    private String isShow;

}
