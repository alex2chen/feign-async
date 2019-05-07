package com.github.feign.configuration;

import com.github.feign.contract.PayContract;
import com.github.feign.core.ContractBean;
import com.github.feign.core.ContractRegistryCenter;
import com.github.feign.core.EurekaClientStarter;
import com.google.common.collect.Lists;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Created by fei.chen on 2018/7/2.
 */
@Configuration
public class FeighAsyncConfiguration {
    @Bean
    public EurekaClientStarter initEurekaClient() {
        return new EurekaClientStarter();
    }

    @Bean
    public ContractRegistryCenter initContractAgent() {
        //TODO:后期（使用扫描来完成自动注入）
        List<ContractBean> contractBeans = Lists.newArrayList();
        contractBeans.add(new ContractBean("github-gps", PayContract.class));
        ContractRegistryCenter registryCenter = new ContractRegistryCenter();
        registryCenter.setContracts(contractBeans);
        return registryCenter;
    }
}
