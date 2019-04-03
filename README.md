## 背景
  为啥要写这个呢？最近一直在关注springcloud，也陆续有一些项目引入了这个技术栈，本项目将对大家最为熟悉的组件feign进行拓展，主要是异步上的支持，毕竟百度搜这个，网上的解决方案比较单一。
    
## 知识储备
### 什么是Feign
[spring-cloud-openfeign 在 Github 描述了其特性](https://github.com/spring-cloud/spring-cloud-openfeign):<p/>
>Declarative REST Client: Feign creates a dynamic implementation of an interface decorated with JAX-RS or Spring MVC annotations.
---
  Feign 支持两种不同的注解（feign的注解和springmvc的注解）来描述接口，简化了 Java HTTP Client 的调用过程，隐藏了实现细节。
  **用法**
  Feign 的精华是一种设计思想，它设计了一种全新的HTTP调用方式，屏蔽了具体的调用细节，与Spring MVC 注解的结合更是极大提高了效率(没有重复造轮子，又设计一套新注解。Hystrix 支持 fallback(降级)的概念，在熔断器打开或发生异常时可以执行默认的代码。如果要对某个@FeignClient 启用 fallback，只需要设置 fallback 属性即可。
```java
@FeignClient(name = "USER", fallbackFactory = UserServiceFallback.class)
public interface UserService {
    @GetMapping("/users/{id}")
    User getUser(@PathVariable("id") String id);
}
```
注：如果你是spring-boot项目这样就可以了，非常简单。

##  功能上（异步支持）
- [x] 支持返回Future

##  该如何入手？
考虑到公司中很多系统都是老系统（基于springmvc3.2.x，非springboot项目），不能直接接入spring-cloud-starter-feign。需要先了解spring-cloud-starter-feign的源码，然后再了解feign-hystrix源码。
### spring-cloud-openfeign源码
针对spring-boot项目，本节依赖的版本为：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-feign</artifactId>
    <version>1.4.4.RELEASE</version>
</dependency> 
```
#### 相关配置
主要有：FeignClientProperties、FeignClientEncodingProperties、FeignHttpClientProperties
```bash
feign.client.config.defalut.connectTimeout=5000
#局部配置
feign.client.config.user.connectTimeout=5000
feign.hystrix.enabled=true
```
#### EnableFeignClients和FeignAutoConfiguration
  第一步：隐式模式（用户不需要做什么，但你要知道），spirng boot会自动加载Feign的配置类FeignAutoConfiguration（spring-cloud-netflix-core-1.4.4.RELEASE.jar/META-INF/spring.factories），为Feign提供运行所需要的环境（各种相关对象）

  第二步：应用系统启动类中添加@EnableFeignClients，它的作用是自动扫描注册标记为 @FeignClient 的用户定义的接口，动态创建实现类（准确的应该叫代理类）并注入到Ioc容器中。

  在调用接口时，会根据接口上的注解信息来创建RequestTemplate，结合实际调用时的参数来创建Request，最后完成调用。
```java
@Import({FeignClientsRegistrar.class})
public @interface EnableFeignClients {
    String[] value() default {};
    String[] basePackages() default {};
    Class<?>[] basePackageClasses() default {};
    Class<?>[] defaultConfiguration() default {};
    Class<?>[] clients() default {};
}
//用于处理 FeignClient 的全局配置和被 @FeignClient 标记的接口
class FeignClientsRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, BeanClassLoaderAware {
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        // 处理默认配置类(EnableFeignClients.defaultConfiguration属性)
        this.registerDefaultConfiguration(metadata, registry);
        // 注册被 @FeignClient 标记的接口
        this.registerFeignClients(metadata, registry);
    }
    private void registerDefaultConfiguration(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> defaultAttrs = metadata.getAnnotationAttributes(EnableFeignClients.class.getName(), true);
        if (defaultAttrs != null && defaultAttrs.containsKey("defaultConfiguration")) {
            String name;
            if (metadata.hasEnclosingClass()) {
                name = "default." + metadata.getEnclosingClassName();
            } else {
                name = "default." + metadata.getClassName();
            }
            this.registerClientConfiguration(registry, name, defaultAttrs.get("defaultConfiguration"));
        }
    }
    public void registerFeignClients(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        // classpath scan工具
        ClassPathScanningCandidateComponentProvider scanner = this.getScanner();
        scanner.setResourceLoader(this.resourceLoader);
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableFeignClients.class.getName());
        // 利用FeignClient作为过滤条件
        AnnotationTypeFilter annotationTypeFilter = new AnnotationTypeFilter(FeignClient.class);
        Class<?>[] clients = attrs == null ? null : (Class[])((Class[])attrs.get("clients"));
        ...
        // 注册
        this.registerFeignClient(registry, annotationMetadata, attributes);
    }
    private void registerFeignClient(BeanDefinitionRegistry registry, AnnotationMetadata annotationMetadata, Map<String, Object> attributes) {
        String className = annotationMetadata.getClassName();
        // 拿到FeignClientFactoryBean的BeanDefinitionBuilder
        BeanDefinitionBuilder definition = BeanDefinitionBuilder.genericBeanDefinition(FeignClientFactoryBean.class);
        this.validate(attributes);
        definition.addPropertyValue("url", this.getUrl(attributes));
        definition.addPropertyValue("path", this.getPath(attributes));
        String name = this.getName(attributes);
        definition.addPropertyValue("name", name);
        definition.addPropertyValue("type", className);
        definition.addPropertyValue("decode404", attributes.get("decode404"));
        definition.addPropertyValue("fallback", attributes.get("fallback"));
        definition.setAutowireMode(2);
        String alias = name + "FeignClient";
        AbstractBeanDefinition beanDefinition = definition.getBeanDefinition();
        beanDefinition.setPrimary(true);
        String qualifier = this.getQualifier(attributes);
        if (StringUtils.hasText(qualifier)) {
            alias = qualifier;
        }        
        BeanDefinitionHolder holder = new BeanDefinitionHolder(beanDefinition, className, new String[]{alias});
        BeanDefinitionReaderUtils.registerBeanDefinition(holder, registry);
    }
}
//自动配置类
@Configuration
@ConditionalOnClass(Feign.class)
@EnableConfigurationProperties({FeignClientProperties.class, FeignHttpClientProperties.class})
public class FeignAutoConfiguration {
 
	@Autowired(required = false)
	private List<FeignClientSpecification> configurations = new ArrayList<>();
	@Bean
	public HasFeatures feignFeature() {
		return HasFeatures.namedFeature("Feign", Feign.class);
	}
	@Bean
	public FeignContext feignContext() {
		//加载FeignClientsConfiguration配置类
		FeignContext context = new FeignContext();
		context.setConfigurations(this.configurations);
		return context;
	}
	@Configuration
	@ConditionalOnClass(name = "feign.hystrix.HystrixFeign")
	protected static class HystrixFeignTargeterConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public Targeter feignTargeter() {
			return new HystrixTargeter();
		}
	}
	@Configuration
	@ConditionalOnMissingClass("feign.hystrix.HystrixFeign")
	protected static class DefaultFeignTargeterConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public Targeter feignTargeter() {
			return new DefaultTargeter();
		}
	}
	// the following configuration is for alternate feign clients if
	// ribbon is not on the class path.
	// see corresponding configurations in FeignRibbonClientAutoConfiguration
	// for load balanced ribbon clients.
	@Configuration
	@ConditionalOnClass(ApacheHttpClient.class)
	@ConditionalOnMissingClass("com.netflix.loadbalancer.ILoadBalancer")
	@ConditionalOnMissingBean(CloseableHttpClient.class)
	@ConditionalOnProperty(value = "feign.httpclient.enabled", matchIfMissing = true)
	protected static class HttpClientFeignConfiguration {
		private final Timer connectionManagerTimer = new Timer(
				"FeignApacheHttpClientConfiguration.connectionManagerTimer", true);
 
		@Autowired(required = false)
		private RegistryBuilder registryBuilder;
		private CloseableHttpClient httpClient;
		@Bean
		@ConditionalOnMissingBean(HttpClientConnectionManager.class)
		public HttpClientConnectionManager connectionManager(
				ApacheHttpClientConnectionManagerFactory connectionManagerFactory,
				FeignHttpClientProperties httpClientProperties) {
			final HttpClientConnectionManager connectionManager = connectionManagerFactory
					.newConnectionManager(httpClientProperties.isDisableSslValidation(), httpClientProperties.getMaxConnections(),
							httpClientProperties.getMaxConnectionsPerRoute(),
							httpClientProperties.getTimeToLive(),
							httpClientProperties.getTimeToLiveUnit(), registryBuilder);
			this.connectionManagerTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					connectionManager.closeExpiredConnections();
				}
			}, 30000, httpClientProperties.getConnectionTimerRepeat());
			return connectionManager;
		}
		@Bean
		public CloseableHttpClient httpClient(ApacheHttpClientFactory httpClientFactory,
				HttpClientConnectionManager httpClientConnectionManager,
				FeignHttpClientProperties httpClientProperties) {
			RequestConfig defaultRequestConfig = RequestConfig.custom()
					.setConnectTimeout(httpClientProperties.getConnectionTimeout())
					.setRedirectsEnabled(httpClientProperties.isFollowRedirects())
					.build();
			this.httpClient = httpClientFactory.createBuilder().
					setConnectionManager(httpClientConnectionManager).
					setDefaultRequestConfig(defaultRequestConfig).build();
			return this.httpClient;
		}
		@Bean
		@ConditionalOnMissingBean(Client.class)
		public Client feignClient(HttpClient httpClient) {
			return new ApacheHttpClient(httpClient);
		}
		@PreDestroy
		public void destroy() throws Exception {
			connectionManagerTimer.cancel();
			if(httpClient != null) {
				httpClient.close();
			}
		}
	}
	@Configuration
	@ConditionalOnClass(OkHttpClient.class)
	@ConditionalOnMissingClass("com.netflix.loadbalancer.ILoadBalancer")
	@ConditionalOnMissingBean(okhttp3.OkHttpClient.class)
	@ConditionalOnProperty(value = "feign.okhttp.enabled")
	protected static class OkHttpFeignConfiguration {
		private okhttp3.OkHttpClient okHttpClient;
		@Bean
		@ConditionalOnMissingBean(ConnectionPool.class)
		public ConnectionPool httpClientConnectionPool(FeignHttpClientProperties httpClientProperties,
													   OkHttpClientConnectionPoolFactory connectionPoolFactory) {
			Integer maxTotalConnections = httpClientProperties.getMaxConnections();
			Long timeToLive = httpClientProperties.getTimeToLive();
			TimeUnit ttlUnit = httpClientProperties.getTimeToLiveUnit();
			return connectionPoolFactory.create(maxTotalConnections, timeToLive, ttlUnit);
		}
		@Bean
		public okhttp3.OkHttpClient client(OkHttpClientFactory httpClientFactory,
										   ConnectionPool connectionPool, FeignHttpClientProperties httpClientProperties) {
			Boolean followRedirects = httpClientProperties.isFollowRedirects();
			Integer connectTimeout = httpClientProperties.getConnectionTimeout();
			Boolean disableSslValidation = httpClientProperties.isDisableSslValidation();
			this.okHttpClient = httpClientFactory.createBuilder(disableSslValidation).
					connectTimeout(connectTimeout, TimeUnit.MILLISECONDS).
					followRedirects(followRedirects).
					connectionPool(connectionPool).build();
			return this.okHttpClient;
		}
		@PreDestroy
		public void destroy() {
			if(okHttpClient != null) {
				okHttpClient.dispatcher().executorService().shutdown();
				okHttpClient.connectionPool().evictAll();
			}
		}
		@Bean
		@ConditionalOnMissingBean(Client.class)
		public Client feignClient() {
			return new OkHttpClient(this.okHttpClient);
		}
	}
}
@Configuration
public class FeignClientsConfiguration {
	@Bean
	@ConditionalOnMissingBean
	public Decoder feignDecoder() {
		return new ResponseEntityDecoder(new SpringDecoder(this.messageConverters));
	}
	@Bean
	@ConditionalOnMissingBean
	public Encoder feignEncoder() {
		return new SpringEncoder(this.messageConverters);
	}
	@Bean
	@ConditionalOnMissingBean
	public Contract feignContract(ConversionService feignConversionService) {
		return new SpringMvcContract(this.parameterProcessors, feignConversionService);
	}
	@Configuration
	@ConditionalOnClass({ HystrixCommand.class, HystrixFeign.class })
	protected static class HystrixFeignConfiguration {
		@Bean
		@Scope("prototype")
		@ConditionalOnMissingBean
		@ConditionalOnProperty(name = "feign.hystrix.enabled", matchIfMissing = false)
		public Feign.Builder feignHystrixBuilder() {
			return HystrixFeign.builder();
		}
	}
	@Bean
	@ConditionalOnMissingBean
	public Retryer feignRetryer() {
		return Retryer.NEVER_RETRY;
	}
	@Bean
	@Scope("prototype")
	@ConditionalOnMissingBean
	public Feign.Builder feignBuilder(Retryer retryer) {
		return Feign.builder().retryer(retryer);
	}
	@Bean
	@ConditionalOnMissingBean(FeignLoggerFactory.class)
	public FeignLoggerFactory feignLoggerFactory() {
		return new DefaultFeignLoggerFactory(logger);
	}
}
```
#### FeignClientFactoryBean
FeignClientFactoryBean是核心，基于每个FeignClient实现了客户端Contract，而feign.target方法就是实例化客户端Contract。这里介绍几组关键的类：
  Targeter提供了对target接口（Feign.Builder.target的封装）
 - DefaultTargeter调用的feign.target（未做任何处理）
 - HystrixTargeter调用的HystrixFeign.Builder.target（集成了Hystrix）
  Client接口提供了execute
 - Client.Default是对Client的实现（基于jdk的get/post）
 - HttpClientFeignConfiguration.feignClient()是封装了LoadBalancerFeignClient和apache HttpClient
 - OkHttpFeignConfiguration.feignClient()是封装了LoadBalancerFeignClient和okhttp
 - LoadBalancerFeignClient提供了负载均衡，它是在FeignRibbonClientAutoConfiguration中通过@Import
  其实读spring cloud-feign源码的技巧就是深入研究FeignClientFactoryBean的依赖，基本上花些时间都可以看懂！
```java
class FeignClientFactoryBean implements FactoryBean<Object>, InitializingBean, ApplicationContextAware {
    //getObject() 返回的是一个SynchronousMethodHandler对象
    public Object getObject() throws Exception {
        FeignContext context = (FeignContext)this.applicationContext.getBean(FeignContext.class);
        Builder builder = this.feign(context);
        String url;
        // 如果FeignClient没有指定URL(配置的是service)
        if (!StringUtils.hasText(this.url)) {
            if (!this.name.startsWith("http")) {
                url = "http://" + this.name;
            } else {
                url = this.name;
            }
            url = url + this.cleanPath();
            // 结合ribbon使得客户端具备负载均衡的能力，默认获取的是LoadBalancerFeignClient
            return this.loadBalance(builder, context, new HardCodedTarget(this.type, this.name, url));
        } else {
            if (StringUtils.hasText(this.url) && !this.url.startsWith("http")) {
                this.url = "http://" + this.url;
            }
            url = this.url + this.cleanPath();
            Client client = (Client)this.getOptional(context, Client.class);
            if (client != null) {
                if (client instanceof LoadBalancerFeignClient) {
                    client = ((LoadBalancerFeignClient)client).getDelegate();
                }
 
                builder.client(client);
            }
            
            return this.targeter.target(this, builder, context, new HardCodedTarget(this.type, this.name, url));
        }
    }
    protected <T> T loadBalance(Builder builder, FeignContext context, HardCodedTarget<T> target) {
        //得到的是 LoadBalancerFeignClient
        Client client = (Client)this.getOptional(context, Client.class);
        if (client != null) {
            builder.client(client);
            // HystrixTargeter
            return this.targeter.target(this, builder, context, target);
        } else {
            throw new IllegalStateException("No Feign Client for loadBalancing defined. Did you forget to include spring-cloud-starter-ribbon?");
        }
    }
}
//Targeter其实就是Feign.Builder的包装
class HystrixTargeter implements Targeter {
    public <T> T target(FeignClientFactoryBean factory, Builder feign, FeignContext context, HardCodedTarget<T> target) {
        if (factory.getFallback() != Void.TYPE && feign instanceof feign.hystrix.HystrixFeign.Builder) {
            Object fallbackInstance = context.getInstance(factory.getName(), factory.getFallback());
            if (fallbackInstance == null) {
                throw new IllegalStateException(String.format("No fallback instance of type %s found for feign client %s", factory.getFallback(), factory.getName()));
            } else if (!target.type().isAssignableFrom(factory.getFallback())) {
                throw new IllegalStateException(String.format("Incompatible fallback instance. Fallback of type %s is not assignable to %s for feign client %s", factory.getFallback(), target.type(), factory.getName()));
            } else {
                feign.hystrix.HystrixFeign.Builder builder = (feign.hystrix.HystrixFeign.Builder)feign;
                return builder.target(target, fallbackInstance);
            }
        } else {
            return feign.target(target);
        }
    }
}
```
### feign-ribbon及feign-hystrix源码
针对非spring-boot项目，本节依赖的版本为：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-feign</artifactId>
    <version>1.4.4.RELEASE</version>
</dependency>
<dependency>
    <groupId>com.netflix.eureka</groupId>
    <artifactId>eureka-client</artifactId>
    <version>1.6.2</version>
</dependency>
<dependency>
    <groupId>com.netflix.ribbon</groupId>
    <artifactId>ribbon-eureka</artifactId>
    <version>2.2.2</version>
    <exclusions>
        <exclusion>
            <groupId>io.reactivex</groupId>
            <artifactId>rxjava</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-ribbon</artifactId>
    <version>9.5.1</version>
</dependency>
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-hystrix</artifactId>
    <version>9.5.1</version>
</dependency>
```
#### 相关配置
项目的resource目录下定义eureka-client.properties（在eureka客户端实例化时通过PropertiesInstanceConfig会读取）
```java
//初始化Eureka Client
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
//手动创建客户端contract
private T createObjectNative() {
    return HystrixFeign.builder()
            .client(RibbonClient.builder().delegate(new DefaultSupport()).build())
            .encoder(new JacksonEncoder(JacksonInstanceManager.getInstance()))
            .decoder(new JacksonDecoder(JacksonInstanceManager.getInstance()))
            .requestInterceptor(request -> {
            .options(new Request.Options(60000, 60000))
            .retryer(Retryer.NEVER_RETRY)
            .target(contract, "http://" + serverName);
}
```
```bash
eureka.serviceUrl.default=http://xxxx.cn/eureka
eureka.region=default
eureka.name=kxtx-test
eureka.vipAddress=
eureka.port=8081
eureka.preferSameZone=true
eureka.shouldUseDns=false
eureka.us-east-1.availabilityZones=default
hystrix.command.default.execution.timeout.enabled=false
kxtx-gps.ribbon.NFLoadBalancerClassName=com.netflix.loadbalancer.DynamicServerListLoadBalancer
kxtx-gps.ribbon.NIWSServerListClassName=com.netflix.niws.loadbalancer.DiscoveryEnabledNIWSServerList
kxtx-gps.ribbon.DeploymentContextBasedVipAddresses=kxtx-gps
```
#### 主要类及其层次结构
![](https://img-blog.csdn.net/20180928000929507 "")
#### Feign及HystrixFeign
Feign创建FeignInvocationHandler，HystrixFeign会创建HystrixInvocationHandler，重点要关注ReflectiveFeign。
```java
//客户端contract
public abstract class Feign {
  public static class Builder {
        //调用target，此时客户端Contract已完成构建
        public <T> T target(Class<T> apiType, String url) {
            return this.target(new HardCodedTarget(apiType, url));
        }
        public <T> T target(Target<T> target) {
            return this.build().newInstance(target);
        }
        //HystrixFeign.Builder执行逻辑
        public Feign build() {
            Factory synchronousMethodHandlerFactory = new Factory(this.client, this.retryer, this.requestInterceptors, this.logger, this.logLevel, this.decode404);
            ParseHandlersByName handlersByName = new ParseHandlersByName(this.contract, this.options, this.encoder, this.decoder, this.errorDecoder, synchronousMethodHandlerFactory);
            return new ReflectiveFeign(handlersByName, this.invocationHandlerFactory);
        }
  }
}
//集成了Hystrix的客户端contract
public final class HystrixFeign {
    //Feign的入口很关键
    public static HystrixFeign.Builder builder() {
        return new HystrixFeign.Builder();
    }
    public static final class Builder extends feign.Feign.Builder {
        //最终build执行逻辑
        Feign build(final FallbackFactory<?> nullableFallbackFactory) {
            //与Hystrix的集成，下面专门讲
            super.invocationHandlerFactory(new InvocationHandlerFactory() {
                public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
                    return new HystrixInvocationHandler(target, dispatch, Builder.this.setterFactory, nullableFallbackFactory);
                }
            });//调用父类
            super.contract(new HystrixDelegatingContract(this.contract));
            return super.build();//调用父类
        }		
	}
}
//客户端contract的真正实现类
public class ReflectiveFeign extends Feign {
    private final InvocationHandlerFactory factory;//很重要的（下面会讲）
    //通过Feign.Builder.target完成构建
    public <T> T newInstance(Target<T> target) {
        //基于contract创建一系列SynchronousMethodHandler
        Map<String, MethodHandler> nameToHandler = this.targetToHandlersByName.apply(target);
        Map<Method, MethodHandler> methodToHandler = new LinkedHashMap();
        List<DefaultMethodHandler> defaultMethodHandlers = new LinkedList();
        Method[] var5 = target.type().getMethods();
        int var6 = var5.length;
        //存至dispatch
        for(int var7 = 0; var7 < var6; ++var7) {
            Method method = var5[var7];
            if (method.getDeclaringClass() != Object.class) {
                if (Util.isDefault(method)) {
                    DefaultMethodHandler handler = new DefaultMethodHandler(method);
                    defaultMethodHandlers.add(handler);
                    methodToHandler.put(method, handler);
                } else {
                    //从nameToHandler.get获取SynchronousMethodHandler
                    methodToHandler.put(method, nameToHandler.get(Feign.configKey(target.type(), method)));
                }
            }
        }
        //创建HystrixInvocationHandler（下面会讲）
        InvocationHandler handler = this.factory.create(target, methodToHandler);
        //再次生成代理类
        T proxy = Proxy.newProxyInstance(target.type().getClassLoader(), new Class[]{target.type()}, handler);
        Iterator var12 = defaultMethodHandlers.iterator();
 
        while(var12.hasNext()) {
            DefaultMethodHandler defaultMethodHandler = (DefaultMethodHandler)var12.next();
            defaultMethodHandler.bindTo(proxy);
        }
 
        return proxy;
    }	
}
//SynchronousMethodHandler的上级代理类（包裹了SynchronousMethodHandler）
public interface InvocationHandlerFactory {
  InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch);
  /**
   * Like {@link InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])}, except for a
   * single method.
   */
  interface MethodHandler {
    Object invoke(Object[] argv) throws Throwable;
  }
  //ReflectiveFeign.factory默认生成的代理是FeignInvocationHandler
  static final class Default implements InvocationHandlerFactory {
    @Override
    public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
      return new ReflectiveFeign.FeignInvocationHandler(target, dispatch);
    }
  }
}
```
#### SynchronousMethodHandler
SynchronousMethodHandle也是一个代理类，最底层的（最终执行）。
```java
//真正执行http请求的类
final class SynchronousMethodHandler implements MethodHandler {
  @Override
  public Object invoke(Object[] argv) throws Throwable {
    RequestTemplate template = buildTemplateFromArgs.create(argv);
    Retryer retryer = this.retryer.clone();
    while (true) {
      try {
        // 执行请求
        return executeAndDecode(template);
      } catch (RetryableException e) {
        retryer.continueOrPropagate(e);
        if (logLevel != Logger.Level.NONE) {
          logger.logRetry(metadata.configKey(), logLevel);
        }
        continue;
      }
    }
  } 
  //通过ParseHandlersByName.apply生成该类
  static class Factory {
      public MethodHandler create(Target<?> target, MethodMetadata md, feign.RequestTemplate.Factory buildTemplateFromArgs, Options options, Decoder decoder, ErrorDecoder errorDecoder) {
        return new SynchronousMethodHandler(target, this.client, this.retryer, this.requestInterceptors, this.logger, this.logLevel, md, buildTemplateFromArgs, options, decoder, errorDecoder, this.decode404);
     }
   }
}
```
#### 与Hystrix的集成
其实ReflectiveFeign.factory就是HystrixInvocationHandler，在HystrixFeign.Builder.build中被构建，HystrixInvocationHandler其实就是将用户的任务（SynchronousMethodHandler）嵌入HystrixCommand中。
```java
public class ReflectiveFeign extends Feign {
    //其实是HystrixInvocationHandler
    private final InvocationHandlerFactory factory;
}
//ReflectiveFeign.factory的另一个代理类的实现
final class HystrixInvocationHandler implements InvocationHandler {
    //源类
    private final Target<?> target;
    //methodToHandler变量，其实就是method和SynchronousMethodHandler映射关系
    private final Map<Method, MethodHandler> dispatch;
    //执行调用
    public Object invoke(Object proxy, final Method method, final Object[] args) throws Throwable {
        if (!"equals".equals(method.getName())) {
            if ("hashCode".equals(method.getName())) {
                return this.hashCode();
            } else if ("toString".equals(method.getName())) {
                return this.toString();
            } else {
                //HystrixCommand就是Hystrix-core里面的，后续文章会讲
                HystrixCommand<Object> hystrixCommand = new HystrixCommand<Object>((Setter)this.setterMethodMap.get(method)) {
                    protected Object run() throws Exception {
                        try {
                            return ((MethodHandler)HystrixInvocationHandler.this.dispatch.get(method)).invoke(args);
                        } catch (Exception var2) {
                            throw var2;
                        } catch (Throwable var3) {
                            throw (Error)var3;
                        }
                    }
 
                    protected Object getFallback() {
                        if (HystrixInvocationHandler.this.fallbackFactory == null) {
                            return super.getFallback();
                        } else {
                            try {
                                Object fallback = HystrixInvocationHandler.this.fallbackFactory.create(this.getExecutionException());
                                Object result = ((Method)HystrixInvocationHandler.this.fallbackMethodMap.get(method)).invoke(fallback, args);
                                if (HystrixInvocationHandler.this.isReturnsHystrixCommand(method)) {
                                    return ((HystrixCommand)result).execute();
                                } else if (HystrixInvocationHandler.this.isReturnsObservable(method)) {
                                    return ((Observable)result).toBlocking().first();
                                } else if (HystrixInvocationHandler.this.isReturnsSingle(method)) {
                                    return ((Single)result).toObservable().toBlocking().first();
                                } else if (HystrixInvocationHandler.this.isReturnsCompletable(method)) {
                                    ((Completable)result).await();
                                    return null;
                                } else {
                                    return result;
                                }
                            } catch (IllegalAccessException var3) {
                                throw new AssertionError(var3);
                            } catch (InvocationTargetException var4) {
                                throw new AssertionError(var4.getCause());
                            }
                        }
                    }
                };
                if (this.isReturnsHystrixCommand(method)) {
                    return hystrixCommand;
                } else if (this.isReturnsObservable(method)) {
                    return hystrixCommand.toObservable();
                } else if (this.isReturnsSingle(method)) {
                    return hystrixCommand.toObservable().toSingle();
                } else {
                    return this.isReturnsCompletable(method) ? hystrixCommand.toObservable().toCompletable() : hystrixCommand.execute();
                }
            }
        } else {
            try {
                Object otherHandler = args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                return this.equals(otherHandler);
            } catch (IllegalArgumentException var5) {
                return false;
            }
        }
    }
}
```
#### 与ribbon的集成
这时你可能会问Feign是怎样实现负载均衡的呢？
  这个不难理解，肯定是集成ribbon实现的，其实构建客户端Contract时就有这样的API：
  HystrixFeign.Builder.client(Client client) ，而client就实现了负载均衡。client的创建是通过RibbonClient.builder().delegate(new Client.Default(null, null)).build()完成的。
```java
public interface Client {
  Response execute(Request request, Options options) throws IOException;
  //真正执行get/post的Client
  public static class Default implements Client {
    @Override
    public Response execute(Request request, Options options) throws IOException {
      HttpURLConnection connection = convertAndSend(request, options);
      return convertResponse(connection).toBuilder().request(request).build();
    }
 
    HttpURLConnection convertAndSend(Request request, Options options) throws IOException {
      final HttpURLConnection
          connection =
          (HttpURLConnection) new URL(request.url()).openConnection();
      if (connection instanceof HttpsURLConnection) {
        HttpsURLConnection sslCon = (HttpsURLConnection) connection;
        if (sslContextFactory != null) {
          sslCon.setSSLSocketFactory(sslContextFactory);
        }
        if (hostnameVerifier != null) {
          sslCon.setHostnameVerifier(hostnameVerifier);
        }
      }
      connection.setConnectTimeout(options.connectTimeoutMillis());
      connection.setReadTimeout(options.readTimeoutMillis());
      connection.setAllowUserInteraction(false);
      connection.setInstanceFollowRedirects(true);
      connection.setRequestMethod(request.method());
      Collection<String> contentEncodingValues = request.headers().get(CONTENT_ENCODING);
      boolean
          gzipEncodedRequest =
          contentEncodingValues != null && contentEncodingValues.contains(ENCODING_GZIP);
      boolean
          deflateEncodedRequest =
          contentEncodingValues != null && contentEncodingValues.contains(ENCODING_DEFLATE);
      boolean hasAcceptHeader = false;
      Integer contentLength = null;
      for (String field : request.headers().keySet()) {
        if (field.equalsIgnoreCase("Accept")) {
          hasAcceptHeader = true;
        }
        for (String value : request.headers().get(field)) {
          if (field.equals(CONTENT_LENGTH)) {
            if (!gzipEncodedRequest && !deflateEncodedRequest) {
              contentLength = Integer.valueOf(value);
              connection.addRequestProperty(field, value);
            }
          } else {
            connection.addRequestProperty(field, value);
          }
        }
      }
      // Some servers choke on the default accept string.
      if (!hasAcceptHeader) {
        connection.addRequestProperty("Accept", "*/*");
      }
      if (request.body() != null) {
        if (contentLength != null) {
          connection.setFixedLengthStreamingMode(contentLength);
        } else {
          connection.setChunkedStreamingMode(8196);
        }
        connection.setDoOutput(true);
        OutputStream out = connection.getOutputStream();
        if (gzipEncodedRequest) {
          out = new GZIPOutputStream(out);
        } else if (deflateEncodedRequest) {
          out = new DeflaterOutputStream(out);
        }
        try {
          out.write(request.body());
        } finally {
          try {
            out.close();
          } catch (IOException suppressed) { // NOPMD
          }
        }
      }
      return connection;
    }
}
//具备负载均衡的Client
public class RibbonClient implements Client {
    //delegate就是Client.Default
    private final Client delegate;
    private final LBClientFactory lbClientFactory;
    //具备负载均衡的get/post
    public Response execute(Request request, Options options) throws IOException {
        try {
            URI asUri = URI.create(request.url());
            String clientName = asUri.getHost();
            URI uriWithoutHost = cleanUrl(request.url(), clientName);
            RibbonRequest ribbonRequest = new RibbonRequest(this.delegate, request, uriWithoutHost);
            //执行请求AbstractLoadBalancerAwareClient.executeWithLoadBalancer
            return ((RibbonResponse)this.lbClient(clientName).executeWithLoadBalancer(ribbonRequest, new RibbonClient.FeignOptionsClientConfig(options))).toResponse();
        } catch (ClientException var7) {
            propagateFirstIOException(var7);
            throw new RuntimeException(var7);
        }
    }
    //RibbonClient的构造者模式
    public static final class Builder {
        //指定Client
        public RibbonClient.Builder delegate(Client delegate) {
            this.delegate = delegate;
            return this;
        }
        public RibbonClient build() {
            //指定了负载均衡模式：LBClientFactory.Default
            return new RibbonClient((Client)(this.delegate != null ? this.delegate : new Default((SSLSocketFactory)null, (HostnameVerifier)null)), (LBClientFactory)(this.lbClientFactory != null ? this.lbClientFactory : new feign.ribbon.LBClientFactory.Default()));
        }
    }
}
//负载均衡客户端
public interface LBClientFactory {
    public static final class Default implements LBClientFactory {
        public Default() {
        }
        //创建（懒加载设计：基于服务名USER）
        public LBClient create(String clientName) {
            //加载文件eureka-client.properties中USER的配置
            IClientConfig config = ClientFactory.getNamedConfig(clientName, LBClientFactory.DisableAutoRetriesByDefaultClientConfig.class);
            //这牵连到与eureka的交互是非常最复杂的，后续讲
            ILoadBalancer lb = ClientFactory.getNamedLoadBalancer(clientName);
            return LBClient.create(lb, config);
        }
    }
}
//真正实现负载均衡
public abstract class AbstractLoadBalancerAwareClient<S extends ClientRequest, T extends IResponse> extends LoadBalancerContext implements IClient<S, T>, IClientConfigAware {
    //执行请求
    public T executeWithLoadBalancer(final S request, final IClientConfig requestConfig) throws ClientException {
        RequestSpecificRetryHandler handler = this.getRequestSpecificRetryHandler(request, requestConfig);
        LoadBalancerCommand command = LoadBalancerCommand.builder().withLoadBalancerContext(this).withRetryHandler(handler).withLoadBalancerURI(request.getUri()).build();
 
        try {
            return (IResponse)command.submit(new ServerOperation<T>() {
                public Observable<T> call(Server server) {
                    //使用eureka中的具体server(ip+port)构建真实的url，不再是微服务名
                    URI finalUri = AbstractLoadBalancerAwareClient.this.reconstructURIWithServer(server, request.getUri());
                    ClientRequest requestForServer = request.replaceUri(finalUri);
 
                    try {
                        return Observable.just(AbstractLoadBalancerAwareClient.this.execute(requestForServer, requestConfig));
                    } catch (Exception var5) {
                        return Observable.error(var5);
                    }
                }
            }).toBlocking().single();
        } catch (Exception var7) {
            Throwable t = var7.getCause();
            if (t instanceof ClientException) {
                throw (ClientException)t;
            } else {
                throw new ClientException(var7);
            }
        }
    }
}
```
#### 与eureka的集成
你也许会问FeignClient.name为服务名（不是url时）它是如何关联到eureka的呢？  
  其实在LBClientFactory.create中有这样一段： ClientFactory.getNamedLoadBalancer(clientName)，这个其实就是读取了eureka-client.properties文件信息，并根据服务名加载配置，并连接eureka拉取ServerList。
```java
public class ClientFactory {
    //如果不存在则创建一个实例 
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name) {
    	return getNamedLoadBalancer(name, DefaultClientConfigImpl.class);
    }
    //同上
    public static synchronized ILoadBalancer getNamedLoadBalancer(String name, Class<? extends IClientConfig> configClass) {
        ILoadBalancer lb = namedLBMap.get(name);
        if (lb != null) {
            return lb;
        } else {
            try {
                lb = registerNamedLoadBalancerFromProperties(name, configClass);
            } catch (ClientException e) {
                throw new RuntimeException("Unable to create load balancer", e);
            }
            return lb;
        }
    }
    //同上
    public static ILoadBalancer registerNamedLoadBalancerFromclientConfig(String name, IClientConfig clientConfig) throws ClientException {
        if (namedLBMap.get(name) != null) {
            throw new ClientException("LoadBalancer for name " + name + " already exists");
        }
    	ILoadBalancer lb = null;
        try {
            //获取配置项NFLoadBalancerClassName：DynamicServerListLoadBalancer
            String loadBalancerClassName = (String) clientConfig.getProperty(CommonClientConfigKey.NFLoadBalancerClassName);
            //实例化DynamicServerListLoadBalancer
            lb = (ILoadBalancer) ClientFactory.instantiateInstanceWithClientConfig(loadBalancerClassName, clientConfig);                                    
            namedLBMap.put(name, lb);            
            logger.info("Client: {} instantiated a LoadBalancer: {}", name, lb);
            return lb;
        } catch (Throwable e) {           
           throw new ClientException("Unable to instantiate/associate LoadBalancer with Client:" + name, e);
        }    	
    }
}
public class DynamicServerListLoadBalancer<T extends Server> extends BaseLoadBalancer {
    //instantiateInstanceWithClientConfig中执行它
    public DynamicServerListLoadBalancer() {
        super();
    }
    //instantiateInstanceWithClientConfig中执行它
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        try {
            super.initWithNiwsConfig(clientConfig);
            //获取配置项NIWSServerListClassName：DiscoveryEnabledNIWSServerList
            String niwsServerListClassName = clientConfig.getPropertyAsString(
                    CommonClientConfigKey.NIWSServerListClassName,
                    DefaultClientConfigImpl.DEFAULT_SEVER_LIST_CLASS);
            //实例化DiscoveryEnabledNIWSServerList
            ServerList<T> niwsServerListImpl = (ServerList<T>) ClientFactory
                    .instantiateInstanceWithClientConfig(niwsServerListClassName, clientConfig);
            this.serverListImpl = niwsServerListImpl;
 
            if (niwsServerListImpl instanceof AbstractServerList) {
                AbstractServerListFilter<T> niwsFilter = ((AbstractServerList) niwsServerListImpl)
                        .getFilterImpl(clientConfig);
                niwsFilter.setLoadBalancerStats(getLoadBalancerStats());
                this.filter = niwsFilter;
            }
 
            String serverListUpdaterClassName = clientConfig.getPropertyAsString(
                    CommonClientConfigKey.ServerListUpdaterClassName,
                    DefaultClientConfigImpl.DEFAULT_SERVER_LIST_UPDATER_CLASS
            );
 
            this.serverListUpdater = (ServerListUpdater) ClientFactory
                    .instantiateInstanceWithClientConfig(serverListUpdaterClassName, clientConfig);
 
            restOfInit(clientConfig);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Exception while initializing NIWSDiscoveryLoadBalancer:"
                            + clientConfig.getClientName()
                            + ", niwsClientConfig:" + clientConfig, e);
        }
    }
}
//eureka服务发现
public class DiscoveryEnabledNIWSServerList extends AbstractServerList<DiscoveryEnabledServer>{
    //instantiateInstanceWithClientConfig中调用
    public DiscoveryEnabledNIWSServerList() {
        //eureka客户端
        this.eurekaClientProvider = new LegacyEurekaClientProvider();
    }
    //instantiateInstanceWithClientConfig中调用
    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {
        clientName = clientConfig.getClientName();
        vipAddresses = clientConfig.resolveDeploymentContextbasedVipAddresses();
        if (vipAddresses == null &&
                ConfigurationManager.getConfigInstance().getBoolean("DiscoveryEnabledNIWSServerList.failFastOnNullVip", true)) {
            throw new NullPointerException("VIP address for client " + clientName + " is null");
        }
        isSecure = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.IsSecure, "false"));
        prioritizeVipAddressBasedServers = Boolean.parseBoolean(""+clientConfig.getProperty(CommonClientConfigKey.PrioritizeVipAddressBasedServers, prioritizeVipAddressBasedServers));
        datacenter = ConfigurationManager.getDeploymentContext().getDeploymentDatacenter();
        targetRegion = (String) clientConfig.getProperty(CommonClientConfigKey.TargetRegion);
 
        shouldUseIpAddr = clientConfig.getPropertyAsBoolean(CommonClientConfigKey.UseIPAddrForServer, DefaultClientConfigImpl.DEFAULT_USEIPADDRESS_FOR_SERVER);
 
        // override client configuration and use client-defined port
        if(clientConfig.getPropertyAsBoolean(CommonClientConfigKey.ForceClientPortConfiguration, false)){
 
            if(isSecure){
 
                if(clientConfig.containsProperty(CommonClientConfigKey.SecurePort)){
 
                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.SecurePort, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;
 
                }else{
                    logger.warn(clientName + " set to force client port but no secure port is set, so ignoring");
                }
            }else{
 
                if(clientConfig.containsProperty(CommonClientConfigKey.Port)){
 
                    overridePort = clientConfig.getPropertyAsInteger(CommonClientConfigKey.Port, DefaultClientConfigImpl.DEFAULT_PORT);
                    shouldUseOverridePort = true;
 
                }else{
                    logger.warn(clientName + " set to force client port but no port is set, so ignoring");
                }
            }
        }
    }
}
```

## 快速开始([QuickStart](src/test/java/com/github/feign/QuickStart.java))
### 第一步，自定义contract
```java
    @RequestLine("POST /kxtx-gps/pay/getStatus")
    Future<Map<String, Object>> getPayStatusSupplyAsync(Map<String, List<String>> batchNos);
```
### 第二步，实例化contract及调用
```java
    List<ContractBean> contractBeans = Lists.newArrayList();
    contractBeans.add(new ContractBean("kxtx-gps", PayContract.class));
    ContractRegistryCenter registryCenter = new ContractRegistryCenter();
    registryCenter.setContracts(contractBeans);
    return registryCenter;
```
```java
    //异步
    Future<Map<String, Object>> result2 = payContract.getPayStatusSupplyAsync(Maps.newHashMap("orderIds", Lists.newArrayList("09fd82b3fb084438a245d564dc8af965")));
    System.out.println("end。");
```

### 尚需完善（不足）
    上面的方案的确是抛弃了很多Feign很多特效，比如原本知道断路器方面就不行，那要怎么弥补呢？
    首先我们看看源码，HystrixFeign》HystrixInvocationHandler.invoke，这其实就是一个命令模式，使用到了rxjava高级库，那问题来了？
    rxjava简直就是雌雄(同步/异步)同体，如果它有提供对外api(Single、Completable)，那是不是就可以放弃上面的的做法，自己去兼容实现呢？
    答案是肯定的，不过这个要对rxjava足够的了解。看下面关键代码：
```java
    //引用HystrixInvocationHandler的148行
    if (isReturnsHystrixCommand(method)) {
      return hystrixCommand;
    } else if (isReturnsObservable(method)) {
      // Create a cold Observable
      return hystrixCommand.toObservable();
    } else if (isReturnsSingle(method)) {
      // Create a cold Observable as a Single
      return hystrixCommand.toObservable().toSingle();
    } else if (isReturnsCompletable(method)) {
      return hystrixCommand.toObservable().toCompletable();
    }
    return hystrixCommand.execute();

```

