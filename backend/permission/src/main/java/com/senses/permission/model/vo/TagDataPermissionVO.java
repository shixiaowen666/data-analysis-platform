package com.senses.permission.model.vo;

import com.senses.permission.entity.TagFieldRelation;
import lombok.Data;

import java.util.List;

/**
 * 行权限展示
 *
 * @author wanjie
 * @date 2025-08-27 10:42
 * @version 1.0
 */
@Data
public class TagDataPermissionVO {

    private String relationType;

    private Long id;

    private List<TagFieldRelation> tagDataPermissions;
}
