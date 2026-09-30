package com.bi.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bi.entity.ChatModelQA;
import com.bi.entity.ChatRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatRecordMapper extends BaseMapper<ChatRecord> {
    @Select("<script>" +
            "select \n" +
            "    distinct " +
            "    dcr.id as id, \n" +
            "    dcr.chat_session_id as chatSessionId, \n" +
            "    dcr.chat_name as chatName, \n" +
            "    dcr.ai_body_code as aiBodyCode, \n" +
            "    dcr.minio_file_path as minioFilePath, \n" +
            "    dcr.updated_at as updatedAt\n" +
            "from dcar_chat_record dcr\n" +
            "where 1=1 \n" +
            "   <if test=\"userId != null\">\n" +
            "       and dcr.created_by = #{userId}\n" +
            "   </if>\n"  +
            "   <if test=\"tenantId != null\">\n" +
            "       and dcr.tenant_id = #{tenantId}\n" +
            "   </if>\n" +
            "   <if test=\"keyword != null and keyword != ''\">\n" +
            "       and dcr.chat_session_id in (\n" +
            "           select distinct dcmq.chat_session_id\n" +
            "           from dcar_chat_model_qa dcmq\n" +
            "           where dcmq.question like concat('%', #{keyword}, '%')\n" +
            "           <if test=\"userId != null\">\n" +
            "               and dcmq.created_by = #{userId}\n" +
            "           </if>\n" +
            "           <if test=\"tenantId != null\">\n" +
            "               and dcmq.tenant_id = #{tenantId}\n" +
            "           </if>\n" +
            "       )\n" +
            "   </if>\n" +
            "order by dcr.updated_at desc " +
            "limit 1000" +
            "</script>")
    List<ChatRecord> getRecordList(@Param("userId") Long userId, @Param("tenantId") Long tenantId, @Param("keyword") String keyword);
    @Select("<script>" +
            "SELECT\n" +
            "        dcmq.chat_session_id,\n" +
            "        dcmq.chat_id,\n" +
            "        dcmq.item_id,\n" +
            "        dcmq.type,\n" +
            "        dcmq.status,\n" +
            "        dcmq.created_by,\n" +
            "        dcmq.created_at,\n" +
            "        dcmq.updated_at,\n" +
            "        dcmq.record_id,\n" +
            "        dcmq.minio_file_path,\n" +
            "        dcmq.history_data_minio_path\n" +
            "        FROM dcar_chat_model_qa dcmq\n" +
            "        WHERE 1 = 1\n" +
            "        <if test=\"chatSessionId != null and chatSessionId != ''\">\n" +
            "            AND dcmq.chat_session_id = #{chatSessionId}\n" +
            "        </if>\n" +
            "        <if test=\"chatId != null and chatId != ''\">\n" +
            "            AND dcmq.chat_id = #{chatId}\n" +
            "        </if>\n" +
            "        ORDER BY MIN(dcmq.created_at) OVER (PARTITION BY dcmq.chat_id) ASC, dcmq.item_id" +
            "</script>")
    List<ChatModelQA> getDcarChatModelQAs(@Param("chatSessionId") String chatSessionId,
                                              @Param("chatId") String chatId);

    @Select("SELECT " +
            "    dcmq.chat_session_id, " +
            "    dcmq.chat_id, " +
            "    dcmq.item_id, " +
            "    dcmq.type, " +
            "    dcmq.status, " +
            "    dcmq.created_by, " +
            "    dcmq.created_at, " +
            "    dcmq.updated_at, " +
            "    dcmq.record_id, " +
            "    dcmq.minio_file_path, " +
            "    dcmq.history_data_minio_path " +
            "FROM dcar_chat_model_qa dcmq " +
            "WHERE dcmq.chat_session_id = #{chatSessionId} " +
            "  AND dcmq.chat_id = #{chatId} " +
            "  AND dcmq.item_id = #{itemId} " +
            "LIMIT 1")
    ChatModelQA getDcarChatModelQA(@Param("chatSessionId") String chatSessionId,
                                   @Param("chatId") String chatId,
                                   @Param("itemId") Integer itemId);
}
