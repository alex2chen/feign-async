package com.github.feign.async;

import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.hystrix.*;

import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

/**
 * Created by fei.chen on 2018/7/2.
 */
public class CompletableFeign {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder extends Feign.Builder {

        private Contract contract = new Contract.Default();
        private SetterFactory setterFactory = new SetterFactory.Default();
        private Executor executor = ForkJoinPool.commonPool();

        public Builder setterFactory(SetterFactory setterFactory) {
            this.setterFactory = setterFactory;
            return this;
        }

        public <T> T target(Target<T> target, T fallback) {
            return build(fallback != null ? new FallbackFactory.Default<T>(fallback) : null)
                    .newInstance(target);
        }

        public <T> T target(Target<T> target, FallbackFactory<? extends T> fallbackFactory) {
            return build(fallbackFactory).newInstance(target);
        }

        public <T> T target(Class<T> apiType, String url, T fallback) {
            return target(new Target.HardCodedTarget<T>(apiType, url), fallback);
        }

        public <T> T target(Class<T> apiType, String url, FallbackFactory<? extends T> fallbackFactory) {
            return target(new Target.HardCodedTarget<T>(apiType, url), fallbackFactory);
        }

        @Override
        public Feign.Builder invocationHandlerFactory(InvocationHandlerFactory invocationHandlerFactory) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Builder contract(Contract contract) {
            this.contract = contract;
            return this;
        }

        @Override
        public Feign build() {
            return build(null);
        }

        Feign build(final FallbackFactory<?> nullableFallbackFactory) {
            CompletableMethodCall completableMethodCall = new CompletableMethodCall.Default();
            super.invocationHandlerFactory((target, dispatch) -> new CompletableInvocationHandler(target, dispatch, completableMethodCall, this.executor));
            super.contract(new CompletableContract(contract));
            return super.build();
        }

        @Override
        public Builder logLevel(Logger.Level logLevel) {
            return (Builder) super.logLevel(logLevel);
        }

        @Override
        public Builder client(Client client) {
            return (Builder) super.client(client);
        }

        @Override
        public Builder retryer(Retryer retryer) {
            return (Builder) super.retryer(retryer);
        }

        @Override
        public Builder logger(Logger logger) {
            return (Builder) super.logger(logger);
        }

        @Override
        public Builder encoder(Encoder encoder) {
            return (Builder) super.encoder(encoder);
        }

        @Override
        public Builder decoder(Decoder decoder) {
            return (Builder) super.decoder(decoder);
        }

        @Override
        public Builder mapAndDecode(ResponseMapper mapper, Decoder decoder) {
            return (Builder) super.mapAndDecode(mapper, decoder);
        }

        @Override
        public Builder decode404() {
            return (Builder) super.decode404();
        }

        @Override
        public Builder errorDecoder(ErrorDecoder errorDecoder) {
            return (Builder) super.errorDecoder(errorDecoder);
        }

        @Override
        public Builder options(Request.Options options) {
            return (Builder) super.options(options);
        }

        @Override
        public Builder requestInterceptor(RequestInterceptor requestInterceptor) {
            return (Builder) super.requestInterceptor(requestInterceptor);
        }

        @Override
        public Builder requestInterceptors(Iterable<RequestInterceptor> requestInterceptors) {
            return (Builder) super.requestInterceptors(requestInterceptors);
        }
    }
}
