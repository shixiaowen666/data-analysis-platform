package com.chatbi.chat.config;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.models.ThinkVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/api/websocket/{chatId}")
@Component
@Slf4j
public class WebSocketServer {
    // 当前在线连接数
    private static int onlineCount = 0;
    // 存放每个用户对应的WebSocket连接对象,key为chatId
    private static final Map<String, Session> webSocketSessionMap = new ConcurrentHashMap<String, Session>();

    private static final Map<String, List<Long>> timeMap = new ConcurrentHashMap<String, List<Long>>();

    private static final Map<String, String> messageMap = new ConcurrentHashMap<String, String>();

    // 与某个客户端的连接会话，需要通过它来给客户端发送数据
    private Session session;
    // 接收id
    private String chatId = "";
    private static WebSocketServer webSocketServer;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 通过@PostConstruct实现初始化bean之前进行的操作
    @PostConstruct
    public void init() {
        webSocketServer = this;
    }

    /**
     * 连接建立成功调用的方法
     *
     * @param session 连接会话，由框架创建
     * @param chatId  用户id， 为处理用户多点登录都能收到消息，需传该格式custId_HHmmss
     * @author csf
     * @date 2020年8月10日
     */

    @OnOpen
    public void onOpen(Session session, @PathParam("chatId") String chatId) {
        if (!webSocketSessionMap.containsKey(chatId)) {
            this.session = session;
            webSocketSessionMap.put(chatId, session);
            addOnlineCount(); // 在线数加1
            log.info("有新连接[{}]接入,当前websocket连接数为:{}", chatId, getOnlineCount());
        }
        this.chatId = chatId;
        try {
            // 第一次建立连接，推送消息给客户端，只会执行一次。后续的新消息由com.kingengine.plug.redis.RedisReceiver接收到redis订阅消息推送
            Map<String,Object> map = new HashMap<String, Object>();
            map.put("request_id", chatId);
            map.put("message", "连接成功！");
            map.put("timestamp", System.currentTimeMillis() / 1000);
            sendMessage(chatId, JSONObject.toJSONString(map));
        } catch (Exception e) {
            log.error("客户端连接websocket服务异常");
            e.printStackTrace();
        }
    }

    /**
     * 连接关闭调用的方法
     */

    @OnClose
    public Long onClose(@PathParam("chatId") String sessionKey) {
        if (webSocketSessionMap.containsKey(sessionKey)) {
            try {
                webSocketSessionMap.get(sessionKey).close();
                webSocketSessionMap.remove(sessionKey);
            } catch (IOException e) {
                log.error("连接[{}]关闭失败。", sessionKey);
                e.printStackTrace();
            }
            subOnlineCount();
            if(!timeMap.containsKey(sessionKey)){
                timeMap.remove(sessionKey);
            }
            if(!messageMap.containsKey(sessionKey)){
                messageMap.remove(sessionKey);
            }
            log.info("连接[{}]关闭，当前websocket连接数：{}", sessionKey, onlineCount);
        }
        return null;
    }

    /**
     * @Description 发送断开标志位，断开连接，自动关闭连接
     *
     * @param sessionKey
     *
     **/
    public void onNotClose(@PathParam("chatId") String sessionKey) {
        if (webSocketSessionMap.containsKey(sessionKey)) {
            try {
                sendMessage(sessionKey, JSON.toJSONString(setMessageMap(sessionKey,"close")));
                onClose(sessionKey);
            } catch (Exception e) {
                log.error("连接[{}]自动关闭失败。", sessionKey);
                e.printStackTrace();
            }
        }
    }

    /**
     * 关闭WebSocket连接（不发额外消息，close消息已通过正常通道发送）
     */
    public void closeConnection(String sessionKey) {
        Session s = webSocketSessionMap.get(sessionKey);
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                log.error("关闭连接[{}]失败，强制清理", sessionKey);
                webSocketSessionMap.remove(sessionKey);
                subOnlineCount();
            }
        }
    }

    private Map<String,Object> setMessageMap(@PathParam("chatId") String sessionKey,String flag){
        Map<String,Object> map = new HashMap<String, Object>();
        map.put("timestamp", System.currentTimeMillis() / 1000);
        map.put("request_id", sessionKey);
        Map<String,Object> map1 = new HashMap<String, Object>();
        map1.put(flag, Boolean.TRUE);
        map.put("metadata", map1);
        return map;
    }


    /**
     * 接收客户端发送的消息
     *
     * @param message 客户端发送过来的消息
     * @param session websocket会话
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("收到来自客户端" + chatId + "的信息:" + message);
    }

    /**
     * 连接错误时触发
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        try {
            session.close();
        } catch (IOException e) {
            log.error("发生错误，连接[{}]关闭失败。");
            e.printStackTrace();
        }
    }


    /**
     * 给指定的客户端推送消息，可单发和群发
     *
     * @param sessionKeys 发送消息给目标客户端sessionKey，多个逗号"，"隔开1234,2345...
     * @param message
     * @throws IOException
     */
    public void sendMessage(String sessionKeys, String message) {
        if (StringUtils.isNotBlank(sessionKeys)) {
            setSpeed(sessionKeys);
            String[] sessionKeyArr = sessionKeys.split(",");
            for (String key : sessionKeyArr) {
                try {
                    List<Session> sessionList = getLikeByMap(webSocketSessionMap, key);
                    if (sessionList.isEmpty()) {
                        log.warn("找不到匹配的WebSocket会话: {}", key);
                        continue;
                    }
                    for (Session session : sessionList) {
                        try {
                            if (!session.isOpen()) {
                                log.warn("WebSocket会话已关闭: key={}", key);
                                continue;
                            }
                            synchronized (session) {
                                session.getBasicRemote().sendText(message);
                            }
                        } catch (Exception e) {
                            log.error("发送单个WebSocket消息失败: key={}, 错误: {}", key, e.getMessage(), e);
                        }
                    }
                } catch (Exception e) {
                    log.error("发送WebSocket消息批次失败: key={}, 错误: {}", key, e.getMessage(), e);
                }
            }
            log.info("WS推送: key={}, content={}", sessionKeys, message);
        } else {
            log.info("sessionKeys为空，没有目标客户端");
        }
    }


    /**
     * 给当前客户端推送消息，首次建立连接时调用
     */

    public void sendMessage(String message)throws IOException {
        this.session.getBasicRemote().sendText(message);
    }


    /**
     * 检查webSocket连接是否在线
     *
     * @param sesstionKey webSocketMap中维护的key
     * @return 是否在线
     */

    public static boolean checkOnline(String sesstionKey) {
        return webSocketSessionMap.containsKey(sesstionKey);
    }
    /**
     * 获取包含key的所有map值
     *
     * @param map
     * @param keyLike
     * @return
     */

    private List<Session> getLikeByMap(Map<String, Session> map, String keyLike) {
        List<Session> list = new ArrayList<>();
        
        // 先尝试精确匹配 - 最优先
        if (map.containsKey(keyLike)) {
            list.add(map.get(keyLike));
            return list;
        }
        
        // 如果没有精确匹配，再尝试部分匹配
        // 根据业务需求，应该是检查keyLike是否以map中的key为前缀
        // 或者map中的key是否以keyLike为前缀
        for (String key : map.keySet()) {
            // 如果WebSocket连接ID是请求ID的前缀，则匹配
            if (keyLike.startsWith(key) || key.startsWith(keyLike)) {
                list.add(map.get(key));
            }
        }
        
        return list;
    }
    public static synchronized int getOnlineCount() {
        return onlineCount;
    }
    public static synchronized void addOnlineCount() {
        WebSocketServer.onlineCount++;
    }
    public static synchronized void subOnlineCount() {
        WebSocketServer.onlineCount--;
    }

    public ThinkVO setThink(String sessionKey, String endMessage){
        ThinkVO thinkVO = new ThinkVO();
        try {
            int endIndex = -1;
            // endMessage 不为空，最后一条消息需要替换
            if (!StringUtils.isBlank(endMessage)) {
                endIndex = -2;
            }
            // 从 Redis 获取消息
            List<String> messageList = stringRedisTemplate.opsForList().range(sessionKey + "_msg", 0, endIndex);
            log.info("get think，key:{}, messageList:{}", sessionKey + "_msg", JSON.toJSONString(messageList));
            StringBuilder message = new StringBuilder();
            if (CollectionUtils.isNotEmpty(messageList)) {
                for (String str : messageList) {
                    message.append(str);
                }
                if (!StringUtils.isBlank(endMessage)) {
                    message.append(endMessage);
                }
            }
            Long spped = 0L;
            if(timeMap.containsKey(sessionKey)){
                List<Long> list = timeMap.get(sessionKey);
                spped = list.get(1) - list.get(0);
            }
            thinkVO.setMessage(message.toString());
            thinkVO.setSpeed(spped);
        } catch (Exception e) {
            log.error("获取思维链失败，原因是 ::::: {}",e);
            // throw new RuntimeException(e);
        }
        return thinkVO;
    }

    // public void setMessage(String sessionKey, String message){
    //     log.info("threadId:{},sessionKey:{},message:{}",Thread.currentThread().getId(),sessionKey,message);
    //     //处理message
    //     if(!messageMap.containsKey(sessionKey)){
    //         messageMap.put(sessionKey, message);
    //     }else {
    //         messageMap.put(sessionKey, messageMap.get(sessionKey) + message);
    //     }
    // }

    public void setSpeed(String sessionKey){
        List<Long> list;
        if(timeMap.containsKey(sessionKey)){
            list = timeMap.get(sessionKey);
            list.add(1,System.currentTimeMillis()/1000);
        }else {
            list = new ArrayList<>(2);
            list.add(0,System.currentTimeMillis()/1000);
        }
        timeMap.put(sessionKey,list);
    }

    public static Boolean getCheckExitsForCache(String sessionKey){
        return webSocketSessionMap.containsKey(sessionKey);
    }

}
