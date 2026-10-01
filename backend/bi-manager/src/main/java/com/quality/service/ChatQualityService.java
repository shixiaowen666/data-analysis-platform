package com.quality.service;

import com.alibaba.fastjson.JSONObject;
import com.common.result.PageResult;
import com.quality.dto.DiagnosisSaveDTO;
import com.quality.dto.FeedbackSubmitDTO;
import com.quality.dto.PageQuery;
import com.quality.entity.ChatErrorDiagnosis;
import com.quality.entity.ChatErrorTypeDict;
import com.quality.entity.ChatFeedback;

import java.util.List;

/** 问答质量管理：字典 / 反馈 / 诊断 / 链路追踪聚合 */
public interface ChatQualityService {

    List<ChatErrorTypeDict> errorTypes(String scope);

    ChatFeedback submitFeedback(FeedbackSubmitDTO dto);

    ChatFeedback myFeedback(String chatId);

    PageResult<JSONObject> feedbackPage(PageQuery q);

    void updateFeedbackStatus(Long id, Integer status);

    /** 诊断列表：全部会话（trace）左联反馈 + 诊断 */
    PageResult<JSONObject> diagnosisPage(PageQuery q);

    /** 全链路：trace + step_trace + feedback + diagnosis + 自动预判 */
    JSONObject trace(String chatId);

    JSONObject systemBLog(String chatId);

    ChatErrorDiagnosis saveDiagnosis(DiagnosisSaveDTO dto);

    void updateFixStatus(String chatId, Integer fixStatus);

    JSONObject stats(String aiBodyCode, Integer days);
}
