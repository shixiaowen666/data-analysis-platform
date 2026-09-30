package com.chatbi.chat.service;


import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import com.chatbi.chat.models.AIChatVO;
import com.chatbi.chat.models.ChartBaseDTO;
import com.chatbi.chat.models.DataRequestDTO;
import com.chatbi.chat.response.CommonVo;

/**
 * Created by zjd on 2026/6/28 18:55
 */
public interface OlapDataService <T>{

    /**
     * 获取olapdata
     * @param olapTableDataRequestDTO
     * @return
     */
    CommonVo<? extends ChartBaseDTO> getOlapData(DataRequestDTO olapTableDataRequestDTO, long userId) ;

    /**
     *供最新的AI方案使用
     * 20260618
     * @param chatVO 对话的信息
     * @param dataRequestDTO  渲染表头的信息 指标 维度 过滤 时间范围等
     * @param data  数据结果
     * @param message
     **/
    void updateChatRecord(AIChatVO chatVO,DataRequestDTO dataRequestDTO, T data, String message);
}
