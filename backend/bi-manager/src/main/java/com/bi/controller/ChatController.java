package com.bi.controller;

import com.bi.dto.*;
import com.bi.entity.ChatRecord;
import com.bi.service.IChatService;
import com.bi.vo.*;
import com.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 智能问数 - 对话管理
 * <p>
 * Base: /v1/chat
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final IChatService chatService;

//    /**
//     * 创建/切换对话会话
//     */
//    @PostMapping("/session")
//    public R<ChatSessionVO> createSession(@Valid @RequestBody ChatSessionDTO dto) {
//        return R.ok(chatService.createSession(dto));
//    }

    /**
     * 获取某智能体的会话列表
     */
    @GetMapping("/agent/sessions")
    public R<List<ChatSessionVO>> listSessions(@RequestParam String aiBodyCode) {
        return R.ok(chatService.listSessions(aiBodyCode, getCurrentTenantId()));
    }

    /**
     * 删除会话
     */
    @GetMapping("/session/del/{sessionId}")
    public R<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId, getCurrentTenantId());
        return R.ok();
    }

//    /**
//     * 获取会话中的消息记录
//     */
//    @GetMapping("/messages")
//    public R<List<ChatMessageVO>> listMessages(@RequestParam String chatSessionId) {
//        return R.ok(chatService.listMessages(chatSessionId, getCurrentTenantId()));
//    }

//    /**
//     * 发送消息
//     */
//    @PostMapping("/message")
//    public R<ChatMessageVO> sendMessage(@Valid @RequestBody ChatMessageDTO dto) {
//        return R.ok(chatService.sendMessage(dto, getCurrentTenantId()));
//    }

    /**
     * 对话反馈
     */
    @PostMapping("/feedback")
    public R<Void> feedback(@Valid @RequestBody ChatFeedbackDTO dto) {
        chatService.feedback(dto);
        return R.ok();
    }

//    /**
//     * 对话记录分页查询
//     */
//    @GetMapping("/records")
//    public R<ApiPageResult<ChatRecord>> listRecords(@Valid ChatRecordQueryDTO query) {
//        return R.ok(chatService.listRecords(query, getCurrentTenantId()));
//    }
//
//    /**
//     * 新对话（清空当前会话）
//     */
//    @PostMapping("/new-chat")
//    public R<ChatSessionVO> newChat(@Valid @RequestBody ChatSessionDTO dto) {
//        return R.ok(chatService.createSession(dto));
//    }

    private Long getCurrentTenantId() {
        // TODO: 从 SecurityContext / Header 获取当前租户
        return 1L;
    }

    /**
     * 获取历史会话记录列表
     * @return
     */
    @GetMapping("/session/list")
    public R<Map<String, List<ChatRecord>>> getRecordList(@RequestParam(required = false) String keyword){
        return R.ok(chatService.getRecordList(keyword));
    }

    /**
     * 获取会话历史记录详情
     * @param chatSessionId
     * @return
     */
    @GetMapping("/session/info")
    public R<ChatRecordDTO> getInfo(@RequestParam("chatSessionId") String chatSessionId){
        return R.ok(chatService.getSessionInfo(chatSessionId));
    }
    /**
     * 获取问话的执行结果
     */
    @GetMapping("/info")
    public R<ChatDTO> getChatInfo(@RequestParam("chatSessionId") String chatSessionId,@RequestParam("chatId") String chatId){
        return R.ok(chatService.getChatInfo(chatSessionId,chatId));
    }

    /**
     * 获取单步骤的执行结果
     */
    @GetMapping("/step")
    public R<ChatItemInfo> getChatStep(@RequestParam("chatSessionId") String chatSessionId,
                                        @RequestParam("chatId") String chatId,
                                        @RequestParam("itemId") Integer itemId) {
        return R.ok(chatService.getChatStep(chatSessionId, chatId, itemId));
    }
}
