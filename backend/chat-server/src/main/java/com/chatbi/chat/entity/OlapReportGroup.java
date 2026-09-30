package com.chatbi.chat.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("olap_report_group")
public class OlapReportGroup {

    private Long id;
    private String groupCode;
    private String groupName;
    private String groupConfig;
}
