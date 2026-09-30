package com.bi.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class RecommendQuestionVO {

    private Long id;

    private String aiBodyCode;

    private String question;

    private String description;

    private Integer sortOrder;

    private Integer status;

    private List<TagVO> tags;

    private LocalDateTime createdAt;
}
