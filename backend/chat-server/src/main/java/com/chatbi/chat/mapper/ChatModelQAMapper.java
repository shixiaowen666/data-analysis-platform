package com.chatbi.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chatbi.chat.entity.DcarChatModelQA;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChatModelQAMapper extends BaseMapper<DcarChatModelQA> {

    @Select("SELECT * FROM dcar_chat_model_qa " +
            "WHERE chat_session_id = #{chatSessionId} AND chat_id = #{chatId} AND item_id = #{itemId} LIMIT 1")
    DcarChatModelQA selectByChatKey(@Param("chatSessionId") String chatSessionId,
                                    @Param("chatId") String chatId,
                                    @Param("itemId") Integer itemId);
}
