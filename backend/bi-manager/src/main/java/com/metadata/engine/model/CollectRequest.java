package com.metadata.engine.model;

import com.metadata.engine.enums.CollectTypeEnum;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 采集请求参数
 */
@Data
public class CollectRequest {

    private CollectTypeEnum collectType;

    private List<String> tableNames = new ArrayList<>();

    public boolean isFullCollect() {
        return CollectTypeEnum.FULL == collectType;
    }

    public boolean isSelectCollect() {
        return CollectTypeEnum.SELECT == collectType;
    }
}
