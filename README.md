## 背景
    为啥要写这个呢？
    最近一直在关注springcloud，也陆续有一些项目引入了这个技术栈，本项目将对大家最为熟悉的组件feign进行拓展，主要是异步上的支持，毕竟百度搜这个，网上的解决方案比较单一。

##  该如何入手？
    开始源码剥析。。。。
    构建：HystrixFeign》ReflectiveFeign（包含dispatch）
    请求执行过程：FeignInvocationHandler》SynchronousMethodHandler（invoke方法）》Client.Default
    服务注册与监听：LBClientFactory》ClientFactory.getNamedConfig》DynamicServerListLoadBalancer(根据serverId获取serverList)》DiscoveryEnabledNIWSServerList(链接Eureka client)

##  功能上（异步支持）
    至少应该能返回Future吧，先做这个吧，后续再弄异步回调支持

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

