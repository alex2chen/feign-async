package com.github.feign.async;

import feign.InvocationHandlerFactory;
import feign.Target;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * Created by fei.chen on 2018/7/2.
 */
public final class CompletableInvocationHandler implements InvocationHandler {
    private final Target<?> target;
    private final Map<Method, InvocationHandlerFactory.MethodHandler> dispatch;
    private final CompletableMethodCall completableMethodCall;
    private final Executor executor;

    public CompletableInvocationHandler(Target<?> target, Map<Method, InvocationHandlerFactory.MethodHandler> dispatch, CompletableMethodCall completableMethodCall, Executor executor) {
        this.target = target;
        this.dispatch = dispatch;
        this.completableMethodCall = completableMethodCall;
        this.executor = executor;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return ((InvocationHandlerFactory.MethodHandler) this.dispatch.get(method)).invoke(args);
        } else if (Future.class.isAssignableFrom(method.getReturnType())) {
            return this.completableMethodCall.create(this.dispatch, method, args, this.executor);
        } else {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("equals")) {
                    if (args[0] == null) {
                        return Boolean.valueOf(false);
                    }
                    try {
                        InvocationHandler e = Proxy.getInvocationHandler(args[0]);
                        if (e.getClass().equals(CompletableInvocationHandler.class)) {
                            CompletableInvocationHandler that = (CompletableInvocationHandler) e;
                            return Boolean.valueOf(this.target.equals(that.target));
                        }
                    } catch (IllegalArgumentException var6) {
                    }
                    return Boolean.valueOf(false);
                }
                if (method.getName().equals("hashCode")) {
                    return Integer.valueOf(this.hashCode());
                }
                if (method.getName().equals("toString")) {
                    return this.toString();
                }
            }
            return ((InvocationHandlerFactory.MethodHandler) this.dispatch.get(method)).invoke(args);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj != null && this.getClass() == obj.getClass()) {
            CompletableInvocationHandler that = (CompletableInvocationHandler) obj;
            return this.target.equals(that.target);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return this.target.hashCode();
    }

    @Override
    public String toString() {
        return this.target.toString();
    }
}

