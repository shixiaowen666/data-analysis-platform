package com.bi.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class RecommendQuestionSaveDTO {

    private Long id;

    @NotEmpty(message = "智能体编码不能为空")
    private String aiBodyCode;

    @NotEmpty(message = "问题内容不能为空")
    private String question;

    private String description;

    private Integer sortOrder;

    private List<Long> tagIds;
}
