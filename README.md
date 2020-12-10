[toc]

# Dubbo 中文注释
请切换为 3.0 分支

# 阅读分类

## dubbo-common
### 1 Common
```
org.apache.dubbo.common.Node

org.apache.dubbo.common.compiler.support.ClassUtils
```
### 2 URL
统一资源定位符。
```
org.apache.dubbo.common.URL
```
### 3 SPI
spi 扩展
```
注解
org.apache.dubbo.common.extension.SPI
```
### 4 ThreadPoolFactory
```
接口
org.apache.dubbo.common.threadpool.ThreadPool

默认线程池工厂
org.apache.dubbo.common.threadpool.support.cached.CachedThreadPool

默认线程池工厂
org.apache.dubbo.common.threadpool.support.cached.FixedThreadPool

任务拒绝策略
org.apache.dubbo.common.threadpool.support.AbortPolicyWithReport

线程工厂
org.apache.dubbo.common.utils.NamedThreadFactory

线程工厂
org.apache.dubbo.common.threadlocal.NamedInternalThreadFactory

无线程线程池
org.apache.dubbo.common.threadpool.ThreadlessExecutor
```

## dubbo-container
容器层。
```
接口
org.apache.dubbo.container.Container

spring 容器
org.apache.dubbo.container.spring.SpringContainer
```

## dubbo-cluster

### 0 Cluster
集群管理核心组件。
```
接口
org.apache.dubbo.rpc.cluster.Cluster
```

### 1 LoadBalance
负载均衡组件。
```
接口
org.apache.dubbo.rpc.cluster.LoadBalance

模板
org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance

随机负载均衡策略
org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance

轮询负载均衡策略
org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance

最少连接负载均衡策略
org.apache.dubbo.rpc.cluster.loadbalance.ShortestResponseLoadBalance
```
### 2 Router
路由组件。
```
接口
org.apache.dubbo.rpc.cluster.LoadBalance

模板
org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance

随机负载均衡策略
org.apache.dubbo.rpc.cluster.loadbalance.RandomLoadBalance

轮询负载均衡策略
org.apache.dubbo.rpc.cluster.loadbalance.RoundRobinLoadBalance
```

### 3 Directory
provider url 管理。
```
接口
org.apache.dubbo.rpc.cluster.Directory

模板
org.apache.dubbo.rpc.cluster.directory.AbstractDirectory
```
