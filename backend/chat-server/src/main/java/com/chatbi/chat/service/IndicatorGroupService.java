package com.chatbi.chat.service;

import com.alibaba.fastjson.JSONObject;
import lombok.Data;

import java.util.List;

public interface IndicatorGroupService {

    JSONObject preview(List<ItemRequest> items);

    @Data
    class ItemRequest {
        private String itemType;
        private Long objectId;
        private String defaultSort;
        /** 展示名称（indicator_group 必填，作为表头分组名） */
        private String displayName;
    }
}
