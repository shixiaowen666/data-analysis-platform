package com.bi.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 维度列表项 VO
 */
@Data
public class DimensionVO {

    private Long id;


    private String chineseName;

    private String englishName;


    private String alias;

    /**
     * 1-维度
     */
    private Integer category;

    /**
     * 1-标准维 2-杂项维
     */
    private Integer dimensionType;


    /**
     * 0-未采集 1-全量采集 2-部分采集
     */
    private Integer collectStatus;

    /**
     * 0-草稿 1-审批中 2-已上线 3-已下线
     */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
