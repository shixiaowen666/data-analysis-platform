package com.bi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 推荐问题-标签关联
 */
@Data
@TableName("chat_recommend_question_tag")
public class RecommendQuestionTag implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long questionId;

    private Long tagId;

    private Long tenantId;

    private LocalDateTime createdAt;
}
