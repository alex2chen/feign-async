package com.github.feign.httpclient;

import feign.Client;
import feign.Request;
import feign.Response;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;

/**
 * Created by fei.chen on 2018/6/29.
 */
public class DefaultSupport extends Client.Default {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSupport.class);

    public DefaultSupport() {
        this(null, null);
    }

    public DefaultSupport(SSLSocketFactory sslContextFactory, HostnameVerifier hostnameVerifier) {
        super(sslContextFactory, hostnameVerifier);
    }

    @Override
    public Response execute(Request request, Request.Options options) throws IOException {
        LOGGER.info("request-" + Thread.currentThread().getName() + ">>>>>" + request.toString());
        Response responseStream = super.execute(request, options);
        byte[] bytes = IOUtils.toByteArray(responseStream.body().asInputStream());
        IOUtils.closeQuietly(responseStream.body().asInputStream());
        Response result = responseStream.toBuilder().body(bytes).build();
        LOGGER.info("response-" + Thread.currentThread().getName() + ">>>>" + result.toString());
        return result;
    }
}
