package com.bi.dto;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/***
 * @ClassName ChatItemInfo
 * @Description
 * @Author chenxiwen
 * @Date 3/31/25 9:58 AM
 * @Version 1.0
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatItemInfoDTO {


    private String type;


    private String question;


    private Integer itemId;


    private JSONObject chartData;


    private String updateUser;


    private String updateTime;

    private Integer status;


    private ThinkDTO think;


    private Integer interactionMode;


    private Integer feedback;


    private String reason;


    private Boolean enableContext;

    private String host;
}
