package com.github.feign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.feign.coder.JacksonDecoder;
import com.github.feign.coder.JacksonEncoder;
import feign.*;
import feign.httpclient.ApacheHttpClient;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static feign.Util.*;

/**
 * @Author: alex.chen
 * @Description:
 * @Date: 2020/6/18
 */
public class FeignBuilder_test {
    @Test
    public void manual_rest() throws JsonProcessingException {
        JacksonEncoder encoder = new JacksonEncoder();
        RemoteService service = Feign.builder()
                .encoder(encoder)
                .decoder(new JacksonDecoder())
                .options(new Request.Options(1000, 3500))
                .retryer(new Retryer.Default(5000, 5000, 1))
                .logger(new Slf4jLoggerSupport(FeignBuilder_test.class))
                .logLevel(Logger.Level.BASIC)
                .client(new ApacheHttpClient())
                .target(RemoteService.class, "http://sit.channelcenter.sitgw.yonghui.cn");
        List<String> shops = org.assertj.core.util.Lists.newArrayList("9010");
        Map<String, Object> response = service.search(shops, "http://baidu");
        System.out.println(response);
        Assert.assertNotNull(response);
        response = service.search(encoder.getMapper().writeValueAsString(shops));
        System.out.println(response);
        Assert.assertNotNull(response);
    }

    @Headers({"Content-Type: application/json"})
    public interface RemoteService {
        @RequestLine("POST /location/simple/codes?name={url}")
        Map<String, Object> search(List<String> shops, @Param("url") String url);

        @RequestLine("POST /location/simple/codes")
        @Body("{body}")
            //只接受string json
        Map<String, Object> search(@Param("body") String body);
    }

    public class Slf4jLoggerSupport extends Logger {
        private final org.slf4j.Logger logger;

        public Slf4jLoggerSupport() {
            this(Logger.class);
        }

        public Slf4jLoggerSupport(Class<?> clazz) {
            this(LoggerFactory.getLogger(clazz));
        }

        public Slf4jLoggerSupport(String name) {
            this(LoggerFactory.getLogger(name));
        }

        Slf4jLoggerSupport(org.slf4j.Logger logger) {
            this.logger = logger;
        }

        protected void log(String configKey, String format, Object... args) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug(String.format(methodTag(configKey) + format, args));
            }
        }

        @Override
        protected void logRequest(String configKey, Level logLevel, Request request) {
            if (this.logger.isDebugEnabled()) {
                String bodyText = "";
                if (request.body() != null) {
                    bodyText = request.charset() != null ? new String(request.body(), request.charset()) : null;
                }
                log(configKey, "---> %s %s HTTP/1.1 body: %s", request.method(), request.url(), bodyText);
            }
        }

        @Override
        protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
            if (!this.logger.isDebugEnabled()) {
                return response;
            }
            String reason = response.reason() != null && logLevel.compareTo(Level.NONE) > 0 ? " " + response.reason() : "";
            int status = response.status();
            int bodyLength = 0;
            if (response.body() != null && !(status == 204 || status == 205)) {
                String bodyText = "";
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                bodyLength = bodyData.length;
                if (bodyLength > 0) {
                    bodyText = decodeOrDefault(bodyData, UTF_8, "Binary data");
                }
                log(configKey, "<--- HTTP/1.1 %s%s (%sms) length: %s  body: %s", status, reason, elapsedTime, bodyLength, bodyText);
                return response.toBuilder().body(bodyData).build();
            }
            log(configKey, "<--- HTTP/1.1 %s%s (%sms) (%s-byte body)", status, reason, elapsedTime, bodyLength);
            return response;
        }
    }
}
