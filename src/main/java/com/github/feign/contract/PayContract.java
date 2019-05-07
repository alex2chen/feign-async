package com.github.feign.contract;

import feign.Headers;
import feign.RequestLine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Created by fei.chen on 2018/6/29.
 */
@Headers("Content-Type: application/json")
public interface PayContract {

    @RequestLine("POST /github-gps/pay/getStatus")
    Map<String, Object> getPayStatus(Map<String, List<String>> batchNos);
    @RequestLine("POST /github-gps/pay/getStatus")
    Future<Map<String, Object>> getPayStatusSupplyAsync(Map<String, List<String>> batchNos);
}