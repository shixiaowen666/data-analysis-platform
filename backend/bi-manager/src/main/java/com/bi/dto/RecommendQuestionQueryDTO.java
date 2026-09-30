package com.bi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class RecommendQuestionQueryDTO {

    @NotEmpty(message = "智能体编码不能为空")
    private String aiBodyCode;

    private String keyword;

    private Long tagId;

    private Integer page = 1;

    private Integer pageSize = 20;
}
