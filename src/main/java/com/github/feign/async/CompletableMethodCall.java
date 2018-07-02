package com.github.feign.async;

import feign.InvocationHandlerFactory;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Created by fei.chen on 2018/7/2.
 */
public interface CompletableMethodCall {
    Future<?> create(Map<Method, InvocationHandlerFactory.MethodHandler> var1, Method var2, Object[] var3, Executor var4);

    public static class Default implements CompletableMethodCall {

        @Override
        public Future<?> create(Map<Method, InvocationHandlerFactory.MethodHandler> dispatch, Method method, Object[] args, Executor executor) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return ((InvocationHandlerFactory.MethodHandler) dispatch.get(method)).invoke(args);
                } catch (RuntimeException var4) {
                    throw var4;
                } catch (Throwable var5) {
                    throw new IllegalStateException("异步调用失败.", var5);
                }
            }, executor);
        }
    }
}
