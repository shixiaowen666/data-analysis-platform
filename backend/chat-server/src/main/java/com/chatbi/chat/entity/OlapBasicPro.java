package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("olap_basic_pro")
public class OlapBasicPro implements Serializable {

    private static final long serialVersionUID = 9188146353598683820L;

    private Long id;

    private Long tenantId;

    private Integer category;

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

    private Integer dataSourceType;

    private Integer status;

    private Long principal;

    private String principalName;

    private String principalEmail;

    private Long approver;

    private String approverName;

    private String approverEmail;

    private String isShow;

    private Long createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    private Long updatedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;
}
