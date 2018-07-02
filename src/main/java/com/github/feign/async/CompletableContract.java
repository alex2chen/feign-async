package com.github.feign.async;

import feign.Contract;
import feign.MethodMetadata;
import feign.Util;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by fei.chen on 2018/7/2.
 */
public final class CompletableContract implements Contract {
    private final Contract delegate;

    public CompletableContract(Contract delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
        List metadataList = this.delegate.parseAndValidatateMetadata(targetType);
        Iterator var3 = metadataList.iterator();

        while (var3.hasNext()) {
            MethodMetadata metadata = (MethodMetadata) var3.next();
            Type type = metadata.returnType();
            if (type instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) ParameterizedType.class.cast(type);
                Class rawType = (Class) parameterizedType.getRawType();
                if (Future.class.isAssignableFrom(rawType)) {
                    metadata.returnType(Util.resolveLastTypeParameter(type, rawType));
                }
            }
        }
        return metadataList;
    }
}