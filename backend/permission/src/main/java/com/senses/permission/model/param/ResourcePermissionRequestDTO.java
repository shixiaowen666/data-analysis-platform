package com.senses.permission.model.param;

import lombok.Data;

import java.util.List;

@Data
public class ResourcePermissionRequestDTO {
    private String applicationCode;
    private Integer resourceType;
    private Long userId;
    private List<Long> objectIds;
}