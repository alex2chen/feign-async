package com.github.feign.coder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Created by fei.chen on 2018/6/29.
 */
public class JacksonInstanceManager {
    private static ObjectMapper mapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .configure(SerializationFeature.INDENT_OUTPUT, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static ObjectMapper getInstance() {
        return mapper;
    }
}
