package com.bi.service;

import com.bi.dto.RecommendQuestionQueryDTO;
import com.bi.dto.RecommendQuestionSaveDTO;
import com.bi.vo.ApiPageResult;
import com.bi.vo.RecommendQuestionVO;

public interface IRecommendQuestionService {

    ApiPageResult<RecommendQuestionVO> listQuestions(RecommendQuestionQueryDTO query, Long tenantId);

    void saveQuestion(RecommendQuestionSaveDTO dto, Long tenantId);

    void updateStatus(Long id, Integer status, Long tenantId);

    void deleteQuestion(Long id, Long tenantId);
}
