package com.github.feign;

import com.github.feign.contract.PayContract;
import com.google.common.collect.Lists;
import org.assertj.core.util.Maps;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Map;
import java.util.concurrent.Future;

@RunWith(SpringRunner.class)
@SpringBootTest
public class QuickStart {
    @Autowired
    private PayContract payContract;

    @Test
    public void go_pay() {
        //# feign.hystrix.enabled=false
        //# hystrix.command.default.circuitBreaker.enabled=false
        //同步
        Map<String, Object> result = payContract.getPayStatus(Maps.newHashMap("orderIds", Lists.newArrayList("09fd82b3fb084438a245d564dc8af965")));
        System.out.println(String.format("调用结果:%s", result));
        //异步
        Future<Map<String, Object>> result2 = payContract.getPayStatusSupplyAsync(Maps.newHashMap("orderIds", Lists.newArrayList("09fd82b3fb084438a245d564dc8af965")));
        System.out.println("end。");
    }
}
