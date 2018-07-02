package com.github.feign.core;

import java.io.Serializable;

/**
 * Created by fei.chen on 2018/7/2.
 */
public class ContractBean implements Serializable {
    private String serviceName;
    private Class contract;

    public ContractBean(String serviceName, Class contract) {
        this.serviceName = serviceName;
        this.contract = contract;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public Class getContract() {
        return contract;
    }

    public void setContract(Class contract) {
        this.contract = contract;
    }
}
