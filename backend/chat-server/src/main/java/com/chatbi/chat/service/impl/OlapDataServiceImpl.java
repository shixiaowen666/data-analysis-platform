package com.chatbi.chat.service.impl;

import com.alibaba.fastjson.JSON;
import com.chatbi.chat.config.WebSocketServer;
import com.chatbi.chat.entity.DcarChatModelQA;
import com.chatbi.chat.enums.QueryStatusEnum;
import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import com.chatbi.chat.models.*;
import com.chatbi.chat.request.AIChatParam;
import com.chatbi.chat.response.CommonVo;
import com.chatbi.chat.service.ChatModelQAService;
import com.chatbi.chat.service.OlapDataService;
import com.chatbi.chat.utils.IpUtils;
import com.chatbi.chat.utils.UserUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * olap数据查询服务
 * Created by zjd on 2025/6/12 18:00
 */
@Service
@Slf4j
public class OlapDataServiceImpl<T> implements OlapDataService<T> {

    @Resource
    private ChatModelQAService chatModelQAService;
    @Resource
    private MinioService minioService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    WebSocketServer webSocketServer;

    @Resource
    private UserUtil userUtil;
    private final String path = "chatbi/record";

    @Override
    public CommonVo<? extends ChartBaseDTO> getOlapData(DataRequestDTO olapTableDataRequestDTO, long userId) {
        return null;
    }

    @Override
    public void updateChatRecord(AIChatVO chatVO , DataRequestDTO dataRequestDTO,T data , String stepType) {
        String chatSessionId = chatVO.getChatSessionId();
        String chatId = chatVO.getChatId();
        Integer itemId = chatVO.getItemId();
        //通过itemId获取详情
        if (StringUtils.isBlank(chatSessionId) || StringUtils.isBlank(chatId) || null == itemId) {
            return;
        }
        //准备页面需要反显的数据
        Object chartData = new AiChatQueryVO(dataRequestDTO, data,chatVO);
        String redisKey = chatSessionId+"@"+chatId;
        //思考
        ThinkVO think = webSocketServer.setThink(redisKey, "");
        ChatItemInfo chatItemInfo = new ChatItemInfo("ai", chatVO.getQuestion(), itemId, chartData, userUtil.getUserName(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 1, think, IpUtils.getLocalIp());
        chatItemInfo.setStepType(stepType);
        //这块觉得没啥用先注释掉了20260618
//        if (null != chatItemInfo.getChartData()) {
//            Boolean success = null;
//            try {
//                success = redisTemplate.opsForValue().setIfAbsent(redisKey + "_rowData", JSON.toJSONString(chatItemInfo));
//                if (Boolean.TRUE.equals(success)) {
//                    //设置7天有效期
//                    redisTemplate.expire(redisKey + "_rowData", 7, TimeUnit.DAYS);
//                }
//            } catch (Exception e) {
//                log.error("缓存rowData失败,{}", e.getMessage(), e);
//            }
//        }
        //上传minio
        String filePath = path + "/" + chatSessionId + "/" + chatId + "/" + itemId + ".json";
        minioService.putFileForJson(chatItemInfo, filePath);
        Integer status = QueryStatusEnum.FINISHED.getKey();

        //保存记录到数据库
        DcarChatModelQA dcarChatModelQA = new DcarChatModelQA(chatSessionId, chatId, itemId, chatItemInfo, filePath, userUtil.getUserId(), status);
        chatModelQAService.saveOrUpdateChat(dcarChatModelQA);
    }
}
