package com.github.feign.core;

import com.github.feign.async.CompletableFeign;
import com.github.feign.contract.PayContract;
import com.github.feign.httpclient.DefaultSupport;
import com.github.feign.coder.JacksonDecoder;
import com.github.feign.coder.JacksonEncoder;
import com.github.feign.coder.JacksonInstanceManager;
import feign.Request;
import feign.Retryer;
import feign.hystrix.HystrixFeign;
import feign.ribbon.RibbonClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.util.List;

/**
 * Created by fei.chen on 2018/6/29.
 */
public class ContractRegistryCenter implements InitializingBean, BeanDefinitionRegistryPostProcessor {
    private List<ContractBean> contracts;

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(contracts, "contract is required.");
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
//        BeanDefinitionBuilder b=BeanDefinitionBuilder.rootBeanDefinition(User.class);
//        b.addPropertyValue("name", "admin"+i);
//        registry.registerBeanDefinition("user"+i, b.getBeanDefinition());
        contracts.forEach(x -> {
            RootBeanDefinition contractBean = new RootBeanDefinition();
            contractBean.setBeanClass(ContractFactoryBean.class);
            contractBean.getPropertyValues().add("contract", x.getContract());
            contractBean.getPropertyValues().add("serverName", x.getServiceName());
            registry.registerBeanDefinition(x.getServiceName(), contractBean);
        });
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {
    }

    public void setContracts(List<ContractBean> contracts) {
        this.contracts = contracts;
    }

    private static class ContractFactoryBean<T> implements FactoryBean<T> {
        private Class<T> contract;
        private String serverName;

        @Override
        public T getObject() throws Exception {
            return createObject();
        }

        private T createObject() {
            return CompletableFeign.builder()
                    .client(RibbonClient.builder().delegate(new DefaultSupport()).build())
                    .encoder(new JacksonEncoder(JacksonInstanceManager.getInstance()))
                    .decoder(new JacksonDecoder(JacksonInstanceManager.getInstance()))
                    .options(new Request.Options(60000, 60000))
                    .retryer(Retryer.NEVER_RETRY)
                    .target(contract, "http://" + serverName);
        }

        @Deprecated
        private T createObjectNative() {
            return HystrixFeign.builder()
                    .client(RibbonClient.builder().delegate(new DefaultSupport()).build())
                    .encoder(new JacksonEncoder(JacksonInstanceManager.getInstance()))
                    .decoder(new JacksonDecoder(JacksonInstanceManager.getInstance()))
                    .requestInterceptor(request -> {
//                    System.out.println("request:>>>>>" + request.toString());
                    })
                    .options(new Request.Options(60000, 60000))
                    .retryer(Retryer.NEVER_RETRY)
                    .target(contract, "http://" + serverName);
        }

        @Override
        public Class<?> getObjectType() {
            return contract;
        }

        @Override
        public boolean isSingleton() {
            return true;
        }

        public Class<T> getContract() {
            return contract;
        }

        public void setContract(Class<T> contract) {
            this.contract = contract;
        }

        public String getServerName() {
            return serverName;
        }

        public void setServerName(String serverName) {
            this.serverName = serverName;
        }
    }
}
