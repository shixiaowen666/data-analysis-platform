package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("olap_group_item")
public class OlapGroupItem {

    private Long id;
    private Long groupId;
    private String itemType;
    private Long objectId;
    private String displayName;
    private Integer displayOrder;
    private Integer isRequired;
    private Integer isDefaultVisible;
    private String formatType;
    private String unit;
    private String defaultSort;
    private String defaultFilter;
    private String remark;
    private Long tenantId;
    private Long createdBy;
    private java.time.LocalDateTime createdAt;
    private Long updatedBy;
    private java.time.LocalDateTime updatedAt;
}
