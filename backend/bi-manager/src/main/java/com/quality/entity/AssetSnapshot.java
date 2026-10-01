package com.quality.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 问答质量管理 · asset_snapshot（JSON 列以 String 存取） */
@Data
@TableName("asset_snapshot")
public class AssetSnapshot implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Long changeId;
    private String assetType;
    private String targetModule;
    private String targetId;
    private String field;
    private String snapshotValue;
    private String snapshotVersion;
    private LocalDateTime takenAt;
}
