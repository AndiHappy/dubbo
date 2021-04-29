
#### Dubbo版本 2.6.4 tag，分支名称：zlz_doc

[官方源码分析](https://dubbo.apache.org/zh/docs/v2.7/dev/build/)

第一个：[服务导出](https://dubbo.apache.org/zh/docs/v2.7/dev/source/export-service/)

代码的入口位置：

服务导出的入口方法是 ServiceBean 的 onApplicationEvent。

关于服务的ServiceBean是如何在启动的时候，注册为一个Listener的，见:[ServiceBean注册为Listener](./ServiceBean注册为Listener.md)

onApplicationEvent 是一个事件响应方法:
该方法会在收到 Spring 上下文刷新事件后执行服务导出操作。方法代码如下：

~~~ java
public void onApplicationEvent(ContextRefreshedEvent event) {
    // 是否有延迟导出 && 是否已导出 && 是不是已被取消导出
    if (isDelay() && !isExported() && !isUnexported()) {
        // 导出服务
        export();
    }
}
~~~

