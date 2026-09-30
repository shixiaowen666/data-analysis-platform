package com.bi.vo;

import lombok.Data;

@Data
public class NameCheckVO {

    private boolean exist;

    private String errMsg;

    public static NameCheckVO ok() {
        return new NameCheckVO();
    }

    public static NameCheckVO conflict(String errMsg) {
        NameCheckVO vo = new NameCheckVO();
        vo.setExist(true);
        vo.setErrMsg(errMsg);
        return vo;
    }
}
