package com.chatbi.chat.feign.client;

import com.chatbi.chat.feign.dto.GetDataSqlResponse;
import com.chatbi.chat.feign.dto.QueryDataRequest;
import com.chatbi.chat.feign.dto.QueryDataResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

@FeignClient(name = "data-server", url = "${feign.data-server:}")
public interface QueryServerClient {

    @PostMapping("/api/data-server/getdata")
    QueryDataResponse<GetDataSqlResponse> execute(@Valid @RequestBody QueryDataRequest request);
}
