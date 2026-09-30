package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class IndicatorGroupVO {

    private Long id;

    private String groupCode;

    private String groupName;

    private Integer fieldCount;

    private String subjectDomain;

    private Integer status;

    private String statusName;

    private LocalDateTime updatedAt;
}
