package com.metadata.vo.meta;

import lombok.Data;

/**
 * 远程库表 VO（选表采集弹窗）
 */
@Data
public class RemoteTableVO {

    private String tableName;

    private String tableComment;

    private Long rowCountEstimate;

    private String rowCountDisplay;
}
