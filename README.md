## 背景
    为啥要写这个呢？
    最近一直在关注springcloud，也陆续有一些项目引入了这个技术栈，本项目将对大家最为熟悉的组件feign进行拓展，主要是异步上的支持，毕竟百度搜这个，网上的解决方案比较单一。
    
## 知识储备
### 什么是Feign
[spring-cloud-openfeign 在 Github 描述了其特性](https://github.com/spring-cloud/spring-cloud-openfeign):<p/>
Declarative REST Client: Feign creates a dynamic implementation of an interface decorated with JAX-RS or Spring MVC annotations.<p/>
Feign 支持两种不同的注解（feign的注解和springmvc的注解）来描述接口，简化了 Java HTTP Client 的调用过程，隐藏了实现细节。<p/>
用法<br/>
Feign 的精华是一种设计思想，它设计了一种全新的HTTP调用方式，屏蔽了具体的调用细节，与Spring MVC 注解的结合更是极大提高了效率(没有重复造轮子，又设计一套新注解。Hystrix 支持 fallback(降级)的概念，在熔断器打开或发生异常时可以执行默认的代码。如果要对某个@FeignClient 启用 fallback，只需要设置 fallback 属性即可。
```java
@FeignClient(name = "USER", fallbackFactory = UserServiceFallback.class)
public interface UserService {
    @GetMapping("/users/{id}")
    User getUser(@PathVariable("id") String id);
}
```
注：如果你是spring-boot项目这样就可以了，非常简单。

##  该如何入手？
考虑到公司中很多系统都是老系统（基于springmvc3.2.x，非springboot项目），不能直接接入spring-cloud-starter-feign。需要先了解spring-cloud-starter-feign的源码，然后再了解feign-hystrix源码。

##  功能上（异步支持）
- [x] 支持返回Future

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

