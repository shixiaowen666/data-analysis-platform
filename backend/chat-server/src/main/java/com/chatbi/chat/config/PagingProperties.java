package com.chatbi.chat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "chatbi.paging")
public class PagingProperties {
    private int defaultPage = 1;
    private int defaultPageSize = 20;
    private int downloadPageSize = 1000;
    private int aiPageSize = 5000;
    private int previewPageSize = 10;
}
