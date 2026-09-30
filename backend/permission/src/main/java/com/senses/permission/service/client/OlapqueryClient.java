package com.senses.permission.service.client;

import com.senses.permission.model.ResultData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;


@FeignClient(url = "${feign.olapquery:}", value = "metrics-olap-query", path = "/feign/olapquery")
public interface OlapqueryClient {

    /** 根据创建人删除策略 */
    @PostMapping(value = "/deleteDecisionByCreator")
    ResultData<Void> deleteDecisionByUserId(@RequestParam("creator") Long creator);
}
