package com.github.feign.core;

import com.github.feign.core.DockerInstanceConfig;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryClient;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Created by fei.chen on 2018/6/29.
 */
public class EurekaClientStarter implements InitializingBean, DisposableBean {
    private ApplicationInfoManager applicationInfoManager;
    private EurekaClient eurekaClient;

    @Override
    public void destroy() throws Exception {
        if (this.eurekaClient != null) {
            this.eurekaClient.shutdown();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        initEurekaClient();
    }

    private synchronized void initEurekaClient() {
        DockerInstanceConfig instanceConfig = new DockerInstanceConfig();
        if (this.applicationInfoManager == null) {
            InstanceInfo instanceInfo = (new EurekaConfigBasedInstanceInfoProvider(instanceConfig)).get();
            this.applicationInfoManager = new ApplicationInfoManager(instanceConfig, instanceInfo);
        }
        this.applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.STARTING);
        if (this.eurekaClient == null) {
            this.eurekaClient = new DiscoveryClient(applicationInfoManager, new DefaultEurekaClientConfig());
        }
        DiscoveryManager.getInstance().setDiscoveryClient((DiscoveryClient) this.eurekaClient);
        this.applicationInfoManager.setInstanceStatus(InstanceInfo.InstanceStatus.UP);
    }
}
