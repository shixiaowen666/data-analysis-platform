package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 图表中的筛选条件
 * Created by jixk on 2019/11/25 21:36
 */
@Data
public class DimDTO implements Serializable {

    @NotNull(message = "维度id不能为空")
    private Long id;

    @Schema(description ="维度英文名")
    private String dimKey;

    @Schema(description ="维度中文名")
    private String dimName;
}
