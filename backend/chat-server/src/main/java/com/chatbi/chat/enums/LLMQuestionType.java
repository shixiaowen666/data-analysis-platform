package com.chatbi.chat.enums;

public enum LLMQuestionType {
    //1代表查数，2是归因，3是计算统计
    QUERY_DATA(1),
    ATTRIBUTION_ANALYSIS(2),

    CALCULATE_STATISTICS(3);

    private Integer questionType;

    LLMQuestionType(Integer questionType) {
        this.questionType = questionType;
    }

    public Integer getQuestionType() {
        return questionType;
    }
}
