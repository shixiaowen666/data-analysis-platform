package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class IndicatorGroupDetailVO {

    private Long id;

    private String groupCode;

    private String groupName;

    private String subjectDomain;

    private String description;

    private Integer status;

    private List<IndicatorGroupItemVO> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
