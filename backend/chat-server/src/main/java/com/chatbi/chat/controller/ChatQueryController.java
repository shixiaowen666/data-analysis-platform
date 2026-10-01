package com.chatbi.chat.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.config.QueryTaskManager;
import com.chatbi.chat.config.RedisStreamConsumer;
import com.chatbi.chat.config.WebSocketServer;
import com.chatbi.chat.entity.AiBody;
import com.chatbi.chat.entity.DcarChatModelQA;
import com.chatbi.chat.enums.QueryStatusEnum;
import com.chatbi.chat.feign.client.ResolverClient;
import com.chatbi.chat.models.*;
import com.chatbi.chat.service.AiBodyService;
import com.chatbi.chat.service.MetadataService;
import com.chatbi.chat.service.ChatTraceService;
import com.chatbi.chat.service.ChatModelQAService;
import com.chatbi.chat.service.DcarChatRecordService;
import com.chatbi.chat.request.AIChatParam;
import com.chatbi.chat.response.CommonVo;
import com.chatbi.chat.response.ResultData;
import com.chatbi.chat.enums.ResultCode;
import com.chatbi.chat.service.impl.MinioService;
import com.chatbi.chat.service.impl.OlapDataServiceImpl;
import com.chatbi.chat.utils.IpUtils;
import com.chatbi.chat.utils.UserThreadLocal;
import com.chatbi.chat.utils.UserUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


/**
 * olap 自定义模板表格数据接口
 * Created by jixk on 2020/2/29 12:14
 */
@RestController
@RequestMapping({"/api/chat-server"})
@Tag(name = "自定义模板数据查询")
@Slf4j
public class ChatQueryController {

    @Resource
    private UserUtil userUtil;

    @Autowired
    private ResolverClient resolverClient;
    @Autowired
    private OlapDataServiceImpl olapDataService;


    private final String path = "chatbi/record";


    @Autowired
    WebSocketServer webSocketServer;
    @Autowired
    private MinioService minioService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ChatModelQAService chatModelQAService;

    @Resource
    private DcarChatRecordService dcarChatRecordService;


    @Autowired
    private AiBodyService aiBodyService;

    @Resource
    private MetadataService metadataService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource(name = "chatAsyncExecutor")
    private ExecutorService chatAsyncExecutor;

    @Autowired
    private QueryTaskManager queryTaskManager;

    @Resource
    private ChatTraceService chatTraceService;

    @Operation(summary = "停止问话，结束query")
    @RequestMapping(value = "/chat/stop", method = RequestMethod.GET)
    public CommonVo stopAiChatQuery(@RequestParam("requestId") String requestId) {
        if (StringUtils.isBlank(requestId)) {
            return CommonVo.Builder.fail(ResultCode.BAD_REQUEST, "参数错误");
        }
        // 1. 取消异步任务
        queryTaskManager.cancel(requestId);

        // 2. 关闭 WebSocket 连接
        webSocketServer.onNotClose(requestId);

        // 3. 清理 Redis 缓存
        stringRedisTemplate.delete(requestId + "-user");
        stringRedisTemplate.delete(requestId + "-code");
        stringRedisTemplate.delete(requestId + "_msg");

        // 4. 更新 DB 状态为已终止
        String[] parts = requestId.split("@", 2);
        if (parts.length == 2) {
            try {
                chatModelQAService.updateStatusByChatId(parts[0], parts[1], QueryStatusEnum.STOP.getKey());
                log.info("查询已终止: requestId={}, status=STOP(4)", requestId);
            } catch (Exception e) {
                log.error("更新查询状态为STOP失败, requestId:{}", requestId, e);
            }
        } else {
            log.warn("requestId格式异常，跳过DB状态更新: requestId={}", requestId);
        }
        return CommonVo.Builder.SUCC().initSuccMsg("已终止");
    }

    @Operation(summary = "智能问数查询入口", description = "发起对话查询，若未传chatSessionId则新建会话，返回会话信息+chatId用于后续WebSocket订阅")
    @RequestMapping(value = "/chat", method = RequestMethod.POST)
    public CommonVo biChat(@RequestBody AIChatParam aiChatParam) {
        String chatId=UUID.randomUUID().toString();
        MDC.put("trace_id", chatId);
        log.info("用户发起query请求：{}",JSON.toJSONString(aiChatParam));
        //智能体code
        String code=aiChatParam.getAicode();
        if (StringUtils.isBlank(code)) {
            log.warn("请求缺少智能体编码(aicode)，入参: {}", JSON.toJSONString(aiChatParam));
            return CommonVo.Builder.fail(ResultCode.BAD_REQUEST, "智能体编码(aicode)不能为空");
        }

        AIChatSessionVO sessionVO=new AIChatSessionVO();
        //如果前端没有传入sessionid 则是新会话，需要保存会话信息
        if (StringUtils.isBlank(aiChatParam.getChatSessionId())) {
            //新建session 和 chat 对象
            sessionVO.setChatSessionId(UUID.randomUUID().toString());
            sessionVO.setAiBodyCode(code);
            sessionVO.setChatName(aiChatParam.getQuestion());
            sessionVO.setName(aiBodyService.getAiBodyByCode(code).getName());
            //会话记录入库
            saveChatRecord(sessionVO);

        }else {
            sessionVO.setChatSessionId(aiChatParam.getChatSessionId());
        }
        AIChatVO chatVO = new AIChatVO();
        chatVO.setChatSessionId(sessionVO.getChatSessionId());
        chatVO.setChatId(chatId);
        chatVO.setQuestion(aiChatParam.getQuestion());
        chatVO.setItemId(0);
        //保存用户的item信息
        saveUserItemInfo(chatVO);
        try {
            Map<String, String> mdcContext = MDC.getCopyOfContextMap();
            SaasUser currentUser = UserThreadLocal.get();
            String requestId = sessionVO.getChatSessionId() + "@" + chatVO.getChatId();
            Future<?> future = chatAsyncExecutor.submit(() -> {
                try {
                    log.info("************************************开启异步线程准备去调用大模型拆解模块***************************************");
//                    log.info("延时200ms,让websocket能拿到第一条msg");
                    Thread.sleep(500);
                    // 缓存用户信息到Redis，供getdata/ai使用,getdata/ai被拆解任务调用，拿不到背景信息，例如aicode，靠redis共享
                    String userKey = sessionVO.getChatSessionId() + "@" + chatVO.getChatId() + "-user";
                    stringRedisTemplate.opsForValue().set(userKey, JSON.toJSONString(currentUser), 60, TimeUnit.MINUTES);
                    String codeKey = sessionVO.getChatSessionId() + "@" + chatVO.getChatId() + "-code";
                    stringRedisTemplate.opsForValue().set(codeKey, code, 60, TimeUnit.MINUTES);

                    if (mdcContext != null) {
                        MDC.setContextMap(mdcContext);
                    }
                    if (currentUser != null) {
                        UserThreadLocal.set(currentUser);
                    }
                    JSONArray resolverDims = metadataService.buildAgentDimensions(code);
                    JSONArray resolverInds = metadataService.buildAgentIndicators(code);
                    LLMRequestParam resolverRequest = buildResolverRequest(chatVO, code, resolverInds, resolverDims);
                    resolverRequest.setStreamName(RedisStreamConsumer.getStreamKey());
                    resolverRequest.setChatId(chatVO.getChatId());
                    if (currentUser != null) {
                        resolverRequest.setUserId(currentUser.getId() != null ? String.valueOf(currentUser.getId()) : null);
                        resolverRequest.setUsername(currentUser.getUsername());
                    }
                    log.info("**************************************************");
                    log.info("**** 跨模块调用 开始: service-resolver.analyze ****");
                    log.info("**************************************************");
                    log.info("resolver请求报文:{}", resolverRequest);
                    long callStart = System.currentTimeMillis();
                    JSONObject resolverResult = resolverClient.analyze(resolverRequest);
                    long cost = System.currentTimeMillis() - callStart;
                    log.info("**************************************************");
                    log.info("**** 跨模块调用 结束: service-resolver.analyze, 耗时={}ms ****", cost);
                    log.info("**************************************************");
                    log.info("resolver响应结果:{}", JSON.toJSONString(resolverResult));
                    // 问答质量管理：落 chat_analysis_trace（非致命）
                    chatTraceService.saveAnalysisTrace(
                            sessionVO.getChatSessionId(), chatVO.getChatId(), code,
                            currentUser != null ? currentUser.getTenantId() : null,
                            currentUser != null ? currentUser.getId() : null,
                            currentUser != null ? currentUser.getUsername() : null,
                            chatVO.getQuestion(), resolverRequest.getDatabaseMeta(), resolverResult, cost);
                } catch (Throwable e) {
                    if (Thread.interrupted()) {
                        log.warn("bi/chat异步任务被终止, requestId:{}, chatSessionId:{}", requestId, sessionVO.getChatSessionId());
                    }
                    log.error("bi/chat异步任务异常, requestId:{}, chatSessionId:{}, code:{}", requestId, sessionVO.getChatSessionId(), code, e);
                } finally {
                    queryTaskManager.remove(requestId);
                    MDC.clear();
                    UserThreadLocal.remove();
                }
            });
            queryTaskManager.register(requestId, future);
        } catch (Exception e) {
            log.error("打印code对应指标维度信息失败，code:{}", code, e);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("chatSessionId", chatVO.getChatSessionId());
        map.put("chatId", chatVO.getChatId());
        map.put("host", IpUtils.getLocalIp());
        map.put("timestamp",System.currentTimeMillis());
        log.info("/bi/chat的返回结果：{}",JSON.toJSONString(map));
        return CommonVo.Builder.SUCC().initSuccData(map);
    }

    private LLMRequestParam buildResolverRequest(AIChatVO chatVO, String code, JSONArray availableMetrics, JSONArray availableDimensions) {
        LLMRequestParam targetRequest = new LLMRequestParam();
        String requestId =  chatVO.getChatSessionId()+"@"+chatVO.getChatId();
        String query = chatVO.getQuestion();
        AiBody aiBody = null;
        List<AiBodyKnowledgeInfo> recalledKnowledge = new ArrayList<>();
        try {
            aiBody = aiBodyService.getAiBodyByCode(code);
            if (aiBody != null && CollectionUtils.isNotEmpty(aiBody.getAiBodyKnowledgeInfoList())) {
                recalledKnowledge = aiBody.getAiBodyKnowledgeInfoList();
            }
        } catch (Exception e) {
            log.error("获取智能体知识库失败，code:{}", code, e);
        }
        String knowledgeText = recalledKnowledge.stream()
                .map(AiBodyKnowledgeInfo::getKnowledgeElement)
                .filter(StringUtils::isNotBlank)
                .map(content -> "- " + content)
                .collect(Collectors.joining("\n"));

        JSONObject databaseMeta = new JSONObject(new LinkedHashMap<>());
        databaseMeta.put("available_metrics", availableMetrics);
        databaseMeta.put("available_dimensions", availableDimensions);
        databaseMeta.put("table_summaries", metadataService.buildAgentTableSummaries(code));
        databaseMeta.put("business_context", knowledgeText);

//        query=query+"（注意：如果指标中包含了计算后的指标，则直接查询对应指标，不需要再进行计算，如：同比、环比、最高、平均等）";
        targetRequest.setRequestId(requestId);
        targetRequest.setQuery(query);
        targetRequest.setDatabaseMeta(databaseMeta);
        return targetRequest;
    }

    /**
     * ai查询实时返回接口
     *
     * @param
     * @return
     */
    @RequestMapping(value = "/api/v1/compute_result", method = RequestMethod.POST)
    public void getCompute_result(@RequestBody JSONObject param) {
        log.info("========================中间步骤结果==============================:{}",JSON.toJSONString(param));
//        {
//            "request_id": "12345678@69f48e1f-37a9-47ce-9df5-73f00bc5913b",
//                "step_id": "step_2",
//                "description": "计算新能源发电量占总发电量的占比 - ratio_value",
//                "result_data": {
//            "metadata": {
//                "source_step": "step_2",
//                        "source_operation": "ratio_value",
//                        "description": "Ratio calculation: new_energy_gen / total",
//                        "row_count": 1,
//                        "time_range": {
//                    "start": "2025-06-15",
//                            "end": "2025-06-15"
//                },
//                "lineage": ["step_1", "step_2", "step_2.ratio_value"],
//                "is_scalar": false,
//                        "tags": {}
//            },
//            "columns": [{
//                "name": "ptdate",
//                        "role": "dimension",
//                        "data_type": "date",
//                        "description": "日期",
//                        "is_computed": false,
//                        "nullable": true
//            }, {
//                "name": "region_name",
//                        "role": "dimension",
//                        "data_type": "string",
//                        "description": "地区名",
//                        "is_computed": false,
//                        "nullable": true
//            }, {
//                "name": "total_td_gen",
//                        "role": "measure",
//                        "data_type": "string",
//                        "unit": "MWh",
//                        "description": "总计_统调发电",
//                        "is_computed": false,
//                        "nullable": true
//            }, {
//                "name": "new_energy_gen",
//                        "role": "measure",
//                        "data_type": "string",
//                        "unit": "MWh",
//                        "description": "新能源发电量(万)",
//                        "is_computed": false,
//                        "nullable": true
//            }, {
//                "name": "ratio_value",
//                        "role": "measure",
//                        "data_type": "float",
//                        "unit": "%",
//                        "description": "Ratio of new_energy_gen",
//                        "is_computed": true,
//                        "nullable": true
//            }],
//            "rows": [{
//                "ptdate": "2025-06-15",
//                        "region_name": "广西",
//                        "total_td_gen": "",
//                        "new_energy_gen": ""
//            }]
//        }
//        }
        // 1. 获取request_id（字符串类型）
        String requestId = param.get("request_id") != null ? param.get("request_id").toString() : "";
        // 2. 获取step_id（字符串类型）
        String stepId = param.get("step_id") != null ? param.get("step_id").toString() : "";
        String stepType = param.get("step_type")!= null ? param.get("step_type").toString() : "";
        String[] ids = requestId.split("@");
        String[] stepids = stepId.split("_");
        String chatSessionId=ids[0];
        String chatId=ids[1];
        String itemId=stepids[1];
        String userKey = requestId + "-user";
        String userJson = stringRedisTemplate.opsForValue().get(userKey);
        if (StringUtils.isBlank(userJson)) {
            throw new IllegalStateException("用户信息已过期，请重新发起会话, requestId=" + requestId);
        }
        SaasUser user = JSON.parseObject(userJson, SaasUser.class);
        if (user == null) {
            throw new IllegalStateException("用户信息反序列化失败, requestId=" + requestId);
        }
        UserThreadLocal.set(user);
        //保存会话历史
        AIChatVO chatVO=new AIChatVO();
        chatVO.setChatSessionId(chatSessionId);
        chatVO.setChatId(chatId);
        chatVO.setItemId(Integer.valueOf(itemId));
        chatVO.setQuestion(param.get("description") != null ? param.get("description").toString() : "");
        //通过itemId获取详情
        if (StringUtils.isBlank(chatSessionId) || StringUtils.isBlank(chatId) || null == itemId) {
            log.error("标识请求的三个要素缺失");
            return;
        }
        Object tmpData = param.get("result_data");
//        tmpdata变成queryDataResponse
        //更新历史
        olapDataService.updateChatRecord(chatVO,new DataRequestDTO(),tmpData,stepType);
        //出入参
//        String redisKey = requestId;
//        //思考
//        ThinkVO think = webSocketServer.setThink(redisKey, "");
//        ChatItemInfo chatItemInfo = new ChatItemInfo("ai", chatVO.getQuestion(), Integer.valueOf(itemId), chartData, userUtil.getUserName(),
//                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 1, think, IpUtils.getLocalIp());
//        chatItemInfo.setStepType(stepType);
//
//        try {
//            // 从 Redis 获取消息
//            int endIndex = -1;
//            List<String> messageList = stringRedisTemplate.opsForList().range(redisKey + "_msg", 0, endIndex);
//            StringBuilder message = new StringBuilder();
//            if (CollectionUtils.isNotEmpty(messageList)) {
//                for (String str : messageList) {
//                    message.append(str);
//                }
//            }
//            OlapLogRecord olapLogRecord = new OlapLogRecord();
//            olapLogRecord.setRequestId(requestId);
//            olapLogRecord.setLogContent(message.toString());
//            olapLogRecordDao.updateByRequestId(olapLogRecord);
//        } catch (Exception e) {
//            log.error("保存olap_log_record失败, requestId:{}", requestId, e);
//        }
//
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
//        //上传minio
//        String filePath = path + "/" + chatSessionId + "/" + chatId + "/" + itemId + ".json";
//        minioService.putFileForJson(chatItemInfo, filePath);
//        Integer status = QueryStatusEnum.FINISHED.getKey();
//        //保存记录到数据库
//        DcarChatModelQA dcarChatModelQA = new DcarChatModelQA(chatSessionId, chatId, Integer.valueOf(itemId), chatItemInfo, filePath, userUtil.getUserId(), status);
//        chatModelQAService.saveOrUpdateChat(dcarChatModelQA);
    }



    private void saveChatRecord(AIChatSessionVO sessionVO) {
        //获取租户id 入库
        SaasUser saasUser = UserThreadLocal.get();
        //保存会话记录
        DcarChatRecord chatRecord = DcarChatRecord.builder()
                .chatSessionId(sessionVO.getChatSessionId())
                .aiBodyCode(sessionVO.getAiBodyCode())
                .chatName(sessionVO.getChatName())
                .tenantId(saasUser.getTenantId())
                .build();
        // todo 打日志
        dcarChatRecordService.saveOrUpdateRecord(chatRecord);
    }

    private void saveUserItemInfo(AIChatVO chatVO) {
        String chatSessionId = chatVO.getChatSessionId();
        String chatId=chatVO.getChatId();
        Integer itemId = chatVO.getItemId();
        Long userId = userUtil.getUserId();
        ChatItemInfo chatItemInfo = new ChatItemInfo("user", chatVO.getQuestion(), chatVO.getItemId(), null, userUtil.getUserName(), LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), 0, null, IpUtils.getLocalIp());
        //上传minio
        String filePath = path + "/" + chatSessionId + "/" + chatId + "/" + itemId + ".json";
        minioService.putFileForJson(chatItemInfo, filePath);
        //保存QA记录到数据库
        DcarChatModelQA dcarChatModelQA = new DcarChatModelQA(chatSessionId, chatVO.getChatId(), 0, null, filePath, userId,  1 );
        dcarChatModelQA.setQuestion(chatVO.getQuestion());
        chatModelQAService.saveOrUpdateChat(dcarChatModelQA);
    }

}
