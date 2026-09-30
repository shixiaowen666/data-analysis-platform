package com.bi.dto;

import com.bi.vo.ParseColumnVO;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 注册视图
 */
@Data
public class ViewRegisterDTO {

    private Long id;
    @NotNull(message = "数据源 ID 不能为空")
    private Long sourceId;

    @NotBlank(message = "视图英文名不能为空")
    private String viewEnName;

    @NotBlank(message = "视图中文名不能为空")
    private String viewCnName;

    @NotBlank(message = "SQL 不能为空")
    private String sql;

    private String note;

    private List<ParseColumnVO> columns;
}
