package com.bi.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bi.dto.*;
import com.bi.entity.AiBody;
import com.bi.entity.ChatModelQA;
import com.bi.entity.ChatRecord;
import com.bi.mapper.AiBodyMapper;
import com.bi.mapper.ChatModelQAMapper;
import com.bi.mapper.ChatRecordMapper;
import com.bi.service.IChatService;
import com.bi.vo.*;
import com.common.base.LoginUser;
import com.common.base.UserThreadLocal;
import com.common.exception.BizException;
import com.common.models.SaasUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能问数 - 对话管理 Service 实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl extends ServiceImpl<ChatRecordMapper, ChatRecord> implements IChatService {

    private final ChatRecordMapper chatRecordMapper;
    private final ChatModelQAMapper chatModelQAMapper;
    private final AiBodyMapper aiBodyMapper;
    private final MinioService minioService;
    private static String prefix = "chatbi/record/";
//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public ChatSessionVO createSession(ChatSessionDTO dto) {
//        // 校验智能体是否存在
//        AiBody aiBody = aiBodyMapper.selectOne(
//                new LambdaQueryWrapper<AiBody>().eq(AiBody::getCode, dto.getAiBodyCode()));
//        if (aiBody == null) {
//            throw new BizException(404, "智能体不存在");
//        }
//
//        String sessionId = IdUtil.simpleUUID();
//
//        ChatRecord record = new ChatRecord();
//        record.setChatSessionId(sessionId);
//        record.setAiBodyCode(dto.getAiBodyCode());
//        record.setChatName(dto.getChatName() != null ? dto.getChatName() : "新对话");
//        record.setInteractionMode(dto.getInteractionMode());
//        record.setIsCalculate(dto.getIsCalculate());
//        record.setEnableContext(dto.getEnableContext());
//        record.setCreatedBy(0L);
//        record.setUpdatedBy(0L);
//        record.setStatus(1);
//        chatRecordMapper.insert(record);
//
//        return toSessionVO(record, aiBody.getName());
//    }

    @Override
    public List<ChatSessionVO> listSessions(String aiBodyCode, Long tenantId) {
        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatRecord::getAiBodyCode, aiBodyCode)
                .orderByDesc(ChatRecord::getUpdatedAt);

        List<ChatRecord> records = chatRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量加载智能体名称
        Set<String> codes = records.stream().map(ChatRecord::getAiBodyCode).collect(Collectors.toSet());
        Map<String, String> nameMap = aiBodyMapper.selectList(
                        new LambdaQueryWrapper<AiBody>().in(AiBody::getCode, codes))
                .stream()
                .collect(Collectors.toMap(AiBody::getCode, AiBody::getName, (a, b) -> a));

        return records.stream()
                .map(r -> toSessionVO(r, nameMap.getOrDefault(r.getAiBodyCode(), "")))
                .collect(Collectors.toList());
    }

//    @Override
//    public List<ChatMessageVO> listMessages(String chatSessionId, Long tenantId) {
//        LambdaQueryWrapper<ChatModelQA> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(ChatModelQA::getChatSessionId, chatSessionId)
//                .orderByAsc(ChatModelQA::getCreatedAt);
//
//        List<ChatModelQA> list = chatModelQAMapper.selectList(wrapper);
//        return list.stream().map(this::toMessageVO).collect(Collectors.toList());
//    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void feedback(ChatFeedbackDTO dto) {
        ChatModelQA qa = chatModelQAMapper.selectById(dto.getId());
        if (qa == null) {
            throw new BizException(404, "问答记录不存在");
        }
        qa.setFeedback(dto.getFeedback());
        qa.setReason(dto.getReason());
        chatModelQAMapper.updateById(qa);
    }

//    @Override
//    public ApiPageResult<ChatRecord> listRecords(ChatRecordQueryDTO query, Long tenantId) {
//        LambdaQueryWrapper<ChatRecord> wrapper = new LambdaQueryWrapper<>();
//        wrapper.eq(ChatRecord::getTenantId, tenantId)
//                .eq(StrUtil.isNotBlank(query.getAiBodyCode()), ChatRecord::getAiBodyCode, query.getAiBodyCode())
//                .like(StrUtil.isNotBlank(query.getKeyword()), ChatRecord::getChatName, query.getKeyword())
//                .orderByDesc(ChatRecord::getUpdatedAt);
//
//        Page<ChatRecord> page = chatRecordMapper.selectPage(
//                new Page<>(query.getPage(), query.getPageSize()), wrapper);
//
//        return new ApiPageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
//    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(String chatSessionId, Long tenantId) {
        // 删除会话消息
        chatModelQAMapper.delete(
                new LambdaQueryWrapper<ChatModelQA>()
                        .eq(ChatModelQA::getChatSessionId, chatSessionId)
                        .eq(ChatModelQA::getTenantId, tenantId));
        // 删除会话记录
        chatRecordMapper.delete(
                new LambdaQueryWrapper<ChatRecord>()
                        .eq(ChatRecord::getChatSessionId, chatSessionId)
                        .eq(ChatRecord::getTenantId, tenantId));
    }

//    @Override
//    @Transactional(rollbackFor = Exception.class)
//    public void newChat(String aiBodyCode, Long tenantId) {
//        // 校验智能体
//        AiBody aiBody = aiBodyMapper.selectOne(
//                new LambdaQueryWrapper<AiBody>().eq(AiBody::getCode, aiBodyCode));
//        if (aiBody == null) {
//            throw new BizException(404, "智能体不存在");
//        }
//
//        ChatSessionDTO dto = new ChatSessionDTO();
//        dto.setAiBodyCode(aiBodyCode);
//        dto.setChatName("新对话");
//        createSession(dto);
//    }

    @Override
    public Map<String, List<ChatRecord>> getRecordList(String keyword) {
        Long tenantId = null;
        Long userId = null;
        SaasUser loginUser = UserThreadLocal.get();
        if (loginUser != null) {
            tenantId = loginUser.getTenantId();
            userId = loginUser.getId();
        }
        log.info("{}====={}",userId,tenantId);
        List<ChatRecord> recordList = baseMapper.getRecordList(userId, tenantId, keyword);
        LocalDate today = LocalDate.now();
        Map<String, List<ChatRecord>> recordMap = recordList.stream()
                .collect(Collectors.groupingBy(record -> {
                    LocalDate recordDate = record.getUpdatedAt().toLocalDate();
                    if (recordDate.isEqual(today)) {
                        return "today";
                    } else if (recordDate.isEqual(today.minusDays(1))) {
                        return "yesterday";
                    } else if (recordDate.isAfter(today.minusDays(7))) {
                        return "lastWeek";
                    } else if (recordDate.isAfter(today.minusDays(30))) {
                        return "lastMonth";
                    } else if (recordDate.isAfter(today.minusDays(180))) {
                        return "lastSixMonth";
                    } else {
                        return "moreThanSixMonth";
                    }
                }));
        // 确保每个时间段都有一个空列表，即使没有记录
        recordMap.putIfAbsent("today", new ArrayList<>());
        recordMap.putIfAbsent("yesterday", new ArrayList<>());
        recordMap.putIfAbsent("lastWeek", new ArrayList<>());
        recordMap.putIfAbsent("lastMonth", new ArrayList<>());
        recordMap.putIfAbsent("lastSixMonth", new ArrayList<>());
        recordMap.putIfAbsent("moreThanSixMonth", new ArrayList<>());
        return recordMap;
    }

    @Override
    public ChatRecordDTO getSessionInfo(String chatSessionId) {
        QueryWrapper<ChatRecord> qw = new QueryWrapper<>();
        qw.lambda().eq(ChatRecord::getChatSessionId, chatSessionId)
                .orderByAsc(ChatRecord::getId);
        List<ChatRecord> dcarChatRecords = baseMapper.selectList(qw);
        if(CollectionUtils.isEmpty(dcarChatRecords)){
            return null;
        }
        ChatRecord dcarChatRecord = dcarChatRecords.get(0);
        ChatRecordDTO dcarChatRecordDTO = new ChatRecordDTO();
        dcarChatRecordDTO.setChatName(dcarChatRecord.getChatName());
        dcarChatRecordDTO.setChatSessionId(dcarChatRecord.getChatSessionId());
        dcarChatRecordDTO.setId(dcarChatRecord.getId());
        dcarChatRecordDTO.setAiBodyCode(dcarChatRecord.getAiBodyCode());

        //增加智能体信息
        AiBody detailByCode =null;
        if(StringUtils.isNotBlank(dcarChatRecordDTO.getAiBodyCode())){
            detailByCode=aiBodyMapper.selectOne(new LambdaQueryWrapper<AiBody>().eq(AiBody::getCode, dcarChatRecordDTO.getAiBodyCode()));
        }
        if(null != detailByCode){
            dcarChatRecordDTO.setName(detailByCode.getName());
        }


        List<ChatModelQA> dcarChatList = baseMapper.getDcarChatModelQAs(chatSessionId,null);
        if(CollectionUtils.isEmpty(dcarChatList)){
            return dcarChatRecordDTO;
        }

        List<ChatDTO> dcarChaQaVOList = new ArrayList<>();
        Map<String, List<ChatModelQA>> chatGroupMap = dcarChatList.stream()
                .collect(Collectors.groupingBy(ChatModelQA::getChatId, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<ChatModelQA>> entry : chatGroupMap.entrySet()) {
            ChatDTO chatDTO = new ChatDTO();
            chatDTO.setChatId(entry.getKey());
            List<ChatItemInfo> chatItemInfoList = new ArrayList<>();
            for (ChatModelQA chatModelQA : entry.getValue()) {
                ChatItemInfo chatItemInfo = getInfoFromMinio(chatModelQA.getMinioFilePath());
                if (chatItemInfo != null) {
                    chatItemInfoList.add(chatItemInfo);
                }
            }
            chatDTO.setChatItemInfo(chatItemInfoList);
            dcarChaQaVOList.add(chatDTO);
        }
        dcarChatRecordDTO.setChatInfo(dcarChaQaVOList);
        return dcarChatRecordDTO;
    }

    @Override
    public ChatDTO getChatInfo(String chatSessionId, String chatId) {
        List<ChatModelQA> chatList = baseMapper.getDcarChatModelQAs(chatSessionId, chatId);
        if (CollectionUtils.isEmpty(chatList)) {
            throw new BizException(404, "对话记录不存在");
        }

        ChatDTO chatDTO = new ChatDTO();
        chatDTO.setChatId(chatId);
        List<ChatItemInfo> chatItemInfoList = new ArrayList<>();
        for (ChatModelQA chatModelQA : chatList) {
            ChatItemInfo chatItemInfo = getInfoFromMinio(chatModelQA.getMinioFilePath());
            if (chatItemInfo != null) {
                chatItemInfoList.add(chatItemInfo);
            }
        }
        chatDTO.setChatItemInfo(chatItemInfoList);
        return chatDTO;
    }


    @Override
    public ChatItemInfo getChatStep(String chatSessionId, String chatId, Integer itemId) {
        ChatModelQA qa = baseMapper.getDcarChatModelQA(chatSessionId, chatId, itemId);
        if (qa == null) {
            throw new BizException(404, "步骤记录不存在");
        }
        ChatItemInfo chatItemInfo = getInfoFromMinio(qa.getMinioFilePath());
        if (chatItemInfo == null) {
            throw new BizException(500, "步骤数据读取失败");
        }
        return chatItemInfo;
    }

    // ==================== 私有方法 ====================
    private ChatItemInfo getInfoFromMinio(String fileName){
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream inputStream = minioService.getObject(null, fileName)) {
            ChatItemInfo chatItemInfo = objectMapper.readValue(inputStream, ChatItemInfo.class);
            return chatItemInfo;
        } catch (Exception e) {
            log.error(String.format("从 MinIO 获取文件 %s 内容失败, 原因: %s", fileName, e.getMessage()),e);
        }
        return null;
    }

    private ChatRecord insertRecord(String sessionId, String aiBodyCode, ChatSessionDTO dto) {
        ChatRecord record = new ChatRecord();
        record.setChatSessionId(sessionId != null ? sessionId : IdUtil.simpleUUID());
        record.setAiBodyCode(aiBodyCode);
        record.setChatName(dto.getChatName() != null ? dto.getChatName() : "新对话");
        record.setInteractionMode(dto.getInteractionMode());
        record.setEnableContext(dto.getEnableContext());
        record.setCreatedBy(0L);
        record.setUpdatedBy(0L);
        record.setStatus(1);
        chatRecordMapper.insert(record);
        return record;
    }

    private ChatSessionVO toSessionVO(ChatRecord record, String aiBodyName) {
        ChatSessionVO vo = new ChatSessionVO();
        vo.setId(record.getId());
        vo.setChatSessionId(record.getChatSessionId());
        vo.setAiBodyCode(record.getAiBodyCode());
        vo.setAiBodyName(aiBodyName);
        vo.setChatName(record.getChatName());
        vo.setInteractionMode(record.getInteractionMode());
        vo.setIsCalculate(record.getIsCalculate());
        vo.setEnableContext(record.getEnableContext());
        vo.setCreatedAt(record.getCreatedAt());
        vo.setUpdatedAt(record.getUpdatedAt());
        return vo;
    }

    private ChatMessageVO toMessageVO(ChatModelQA qa) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(qa.getId());
        vo.setChatSessionId(qa.getChatSessionId());
        vo.setChatId(qa.getChatId());
        vo.setType(qa.getType());
        vo.setQuestion(qa.getQuestion());
        vo.setAnswer(qa.getAnswer());
        vo.setFeedback(qa.getFeedback());
        vo.setReason(qa.getReason());
        vo.setStatus(qa.getStatus());
        vo.setCreateTime(qa.getCreatedAt());

        // 如果有数据卡片（通过 answer 中存储的 JSON），需要解析
        // 目前从关联记录中获取（通过 chatId）
        // 简化处理：直接在 AI 记录上挂载 dataCard
        vo.setDataCard(qa.getDataCard());
        return vo;
    }


}
