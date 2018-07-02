package com.github.feign.core;

import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.MyDataCenterInstanceConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by fei.chen on 2018/6/29.
 */
public class DockerInstanceConfig extends MyDataCenterInstanceConfig implements EurekaInstanceConfig {
    public DockerInstanceConfig() {
    }

    @Override
    public String getHostName(boolean refresh) {
        try {
            String e = System.getProperty("host.ip");
            return e != null && !e.trim().equals("") ? e : InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException var3) {
            return super.getHostName(refresh);
        }
    }
}
