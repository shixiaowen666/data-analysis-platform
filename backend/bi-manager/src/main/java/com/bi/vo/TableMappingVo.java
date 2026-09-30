package com.bi.vo;

import lombok.Data;

@Data
public class TableMappingVo {
    private Long tableId;
    private String tableName;
    private String tableComment;
    private String ifRegister;
    private Integer clnNum;
}
