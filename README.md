# Dubbo架构分析学习

#### SPI机制

依赖的是：ExtensionLoader,具体的案例是，master分支上，类型入口为：

~~~
spi.example.ExtensionLoaderExample
~~~

#### 服务的export

#### 服务的调用

服务的调用的入口为：

~~~
 ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("spring/dubbo-consumer.xml");
 context.start();
 DemoService demoService = context.getBean("demoService", DemoService.class);
 demoService.sayHello("ddddd");
~~~

首先就是context的getBean，最终调用的是 ReferenceBean的``` org.apache.dubbo.config.ReferenceConfig.get() ```
至于这其中的机制，见[ReferenceBean配合Spring机制](./ReferenceBean配合Spring机制.md)

