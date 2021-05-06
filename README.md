# Dubbo架构分析学习

#### SPI机制

依赖的是：ExtensionLoader,具体的案例是，master分支上，类型入口为：

~~~
spi.example.ExtensionLoaderExample
~~~

#### 服务的export


#### Dubbo bean服务方法的调用

~~~
 ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer.xml");
 context.start();
 DemoService demoService = context.getBean("demoService", DemoService.class);
 demoService.sayHello("ddddd");
~~~

首先就是context的getBean，最终调用的是 ReferenceBean的``` org.apache.dubbo.config.ReferenceConfig.get() ```
至于这其中的机制，见[ReferenceBean配合Spring机制](./ReferenceBean配合Spring机制.md)

服务的调用的过程:[Dubbo服务调用的过程](./Dubbo服务调用的过程.md)



