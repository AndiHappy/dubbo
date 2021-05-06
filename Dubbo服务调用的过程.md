### Dubbo服务调用
入口为：
~~~
// org.apache.dubbo.config.spring.ReferenceBean.getObject
    /**
     * Return an instance (possibly shared or independent) of the object managed by this factory.
     * ReferenceBean 本身就继承了FactoryBean<T> 接口
     * */
    @Override
    public Object getObject() {
        return get();
    }

// org.apache.dubbo.config.ReferenceConfig.get
    public synchronized T get() {
        if (destroyed) {
            throw new IllegalStateException("The invoker of ReferenceConfig(" + url + ") has already destroyed!");
        }
        if (ref == null) {
            init();
        }
        return ref;
    }
~~~

init() 具体的过程为：
~~~
public synchronized void init() {

//---- 校验的过程-------
        checkAndUpdateSubConfigs();
        checkStubAndLocal(interfaceClass);
        ConfigValidationUtils.checkMock(interfaceClass, this);

//---- 初始化Map的过程--------
        Map<String, String> map = new HashMap<String, String>();
        map.put(SIDE_KEY, CONSUMER_SIDE);
        ReferenceConfigBase.appendRuntimeParameters(map);
        if (!ProtocolUtils.isGeneric(generic)) {
            String revision = Version.getVersion(interfaceClass, version);
            if (revision != null && revision.length() > 0) {
                map.put(REVISION_KEY, revision);
            }

            String[] methods = Wrapper.getWrapper(interfaceClass).getMethodNames();
            if (methods.length == 0) {
                logger.warn("No method found in service interface " + interfaceClass.getName());
                map.put(METHODS_KEY, ANY_VALUE);
            } else {
                map.put(METHODS_KEY, StringUtils.join(new HashSet<String>(Arrays.asList(methods)), COMMA_SEPARATOR));
            }
        }
        map.put(INTERFACE_KEY, interfaceName);
        AbstractConfig.appendParameters(map, getMetrics());
        AbstractConfig.appendParameters(map, getApplication());
        AbstractConfig.appendParameters(map, getModule());
        AbstractConfig.appendParameters(map, consumer);
        AbstractConfig.appendParameters(map, this);

        MetadataReportConfig metadataReportConfig = getMetadataReportConfig();
        if (metadataReportConfig != null && metadataReportConfig.isValid()) {
            map.putIfAbsent(METADATA_KEY, REMOTE_METADATA_STORAGE_TYPE);
        }
        Map<String, AsyncMethodInfo> attributes = null;
        if (CollectionUtils.isNotEmpty(getMethods())) {
            attributes = new HashMap<>();
            for (MethodConfig methodConfig : getMethods()) {
                AbstractConfig.appendParameters(map, methodConfig, methodConfig.getName());
                String retryKey = methodConfig.getName() + ".retry";
                if (map.containsKey(retryKey)) {
                    String retryValue = map.remove(retryKey);
                    if ("false".equals(retryValue)) {
                        map.put(methodConfig.getName() + ".retries", "0");
                    }
                }
                AsyncMethodInfo asyncMethodInfo = AbstractConfig.convertMethodConfig2AsyncInfo(methodConfig);
                if (asyncMethodInfo != null) {
                    attributes.put(methodConfig.getName(), asyncMethodInfo);
                }
            }
        }

        String hostToRegistry = ConfigUtils.getSystemProperty(DUBBO_IP_TO_REGISTRY);
        if (StringUtils.isEmpty(hostToRegistry)) {
            hostToRegistry = NetUtils.getLocalHost();
        }
        map.put(REGISTER_IP_KEY, hostToRegistry);

//-----Map 构建完成后的结果
        /**
         * {mapping-type=metadata,
         * init=false,
         * side=consumer,
         * register.ip=172.25.205.105,
         * release=,
         * methods=sayHello,sayHelloAsync,
         * qos.port=33333,
         * dubbo=2.0.2,
         * pid=82986,
         * check=true,
         * interface=org.apache.dubbo.demo.DemoService,
         * enable.auto.migration=true,
         * mapping.type=metadata,
         * metadata-type=remote,
         * application=demo-consumer,
         * sticky=false,
         * timestamp=1619774075259,
         * enable-auto-migration=true}
         * */
        serviceMetadata.getAttachments().putAll(map);

//----- 最重要的，创建服务，或者说bean的Proxy---------
        ref = createProxy(map);

        serviceMetadata.setTarget(ref);
        serviceMetadata.addAttribute(PROXY_CLASS_REF, ref);
        ConsumerModel consumerModel = repository.lookupReferredService(serviceMetadata.getServiceKey());
        consumerModel.setProxyObject(ref);
        consumerModel.init(attributes);
        initialized = true;
        checkInvokerAvailable();
        // dispatch a ReferenceConfigInitializedEvent since 2.7.4
        dispatch(new ReferenceConfigInitializedEvent(this, invoker));
    }
~~~


创建Proxy的过程：
~~~
private T createProxy(Map<String, String> map) {
        // 
        if (shouldJvmRefer(map)) {
            URL url = new URL(LOCAL_PROTOCOL, LOCALHOST_VALUE, 0, interfaceClass.getName()).addParameters(map);
            invoker = REF_PROTOCOL.refer(interfaceClass, url);
        } else {
            urls.clear();
            if (url != null && url.length() > 0) { // user specified URL, could be peer-to-peer address, or register center's address.
                String[] us = SEMICOLON_SPLIT_PATTERN.split(url);
                if (us != null && us.length > 0) {
                    for (String u : us) {
                        URL url = URL.valueOf(u);
                        if (StringUtils.isEmpty(url.getPath())) {
                            url = url.setPath(interfaceName);
                        }
                        if (UrlUtils.isRegistry(url)) {
                            urls.add(url.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map)));
                        } else {
                            urls.add(ClusterUtils.mergeUrl(url, map));
                        }
                    }
                }
            } else { // assemble URL from register center's configuration
                // if protocols not injvm checkRegistry
                if (!LOCAL_PROTOCOL.equalsIgnoreCase(getProtocol())) {
                    checkRegistry();
                    List<URL> us = ConfigValidationUtils.loadRegistries(this, false);
                    if (CollectionUtils.isNotEmpty(us)) {
                        for (URL u : us) {
                            URL monitorUrl = ConfigValidationUtils.loadMonitor(this, u);
                            if (monitorUrl != null) {
                                map.put(MONITOR_KEY, URL.encode(monitorUrl.toFullString()));
                            }
                            // Note
                            URL urlcons = u.addParameterAndEncoded(REFER_KEY, StringUtils.toQueryString(map));
                            urls.add(urlcons);
                        }
                    }
                    if (urls.isEmpty()) {
                        throw new IllegalStateException("No such any registry to reference " + interfaceName + " on the consumer " + NetUtils.getLocalHost() + " use dubbo version " + Version.getVersion() + ", please config <dubbo:registry address=\"...\" /> to your spring config.");
                    }
                }
            }

            //registry://127.0.0.1:2181/org.apache.dubbo.registry.RegistryService?application=demo-consumer&dubbo=2.0.2&enable-auto-migration=true&enable.auto.migration=true&id=org.apache.dubbo.config.RegistryConfig&mapping-type=metadata&mapping.type=metadata&pid=98099&qos.port=33333&refer=application%3Ddemo-consumer%26check%3Dtrue%26dubbo%3D2.0.2%26enable-auto-migration%3Dtrue%26enable.auto.migration%3Dtrue%26init%3Dfalse%26interface%3Dorg.apache.dubbo.demo.DemoService%26mapping-type%3Dmetadata%26mapping.type%3Dmetadata%26metadata-type%3Dremote%26methods%3DsayHello%2CsayHelloAsync%26pid%3D98099%26qos.port%3D33333%26register.ip%3D172.22.112.54%26side%3Dconsumer%26sticky%3Dfalse%26timestamp%3D1617267020409&registry=zookeeper&timestamp=1617267970132
            if (urls.size() == 1) {
                invoker = REF_PROTOCOL.refer(interfaceClass, urls.get(0));
            } else {
                List<Invoker<?>> invokers = new ArrayList<Invoker<?>>();
                URL registryURL = null;
                for (URL url : urls) {
                    invokers.add(REF_PROTOCOL.refer(interfaceClass, url));
                    if (UrlUtils.isRegistry(url)) {
                        registryURL = url; // use last registry url
                    }
                }
                if (registryURL != null) { // registry url is available
                    // for multi-subscription scenario, use 'zone-aware' policy by default
                    String cluster = registryURL.getParameter(CLUSTER_KEY, ZoneAwareCluster.NAME);
                    // The invoker wrap sequence would be: ZoneAwareClusterInvoker(StaticDirectory) -> FailoverClusterInvoker(RegistryDirectory, routing happens here) -> Invoker
                    invoker = Cluster.getCluster(cluster, false).join(new StaticDirectory(registryURL, invokers));
                } else { // not a registry url, must be direct invoke.
                    String cluster = CollectionUtils.isNotEmpty(invokers)
                            ? (invokers.get(0).getUrl() != null ? invokers.get(0).getUrl().getParameter(CLUSTER_KEY, ZoneAwareCluster.NAME) : Cluster.DEFAULT)
                            : Cluster.DEFAULT;
                    invoker = Cluster.getCluster(cluster).join(new StaticDirectory(invokers));
                }
            }
        }

        if (logger.isInfoEnabled()) {
            logger.info("Refer dubbo service " + interfaceClass.getName() + " from url " + invoker.getUrl());
        }

        URL consumerURL = new URL(CONSUMER_PROTOCOL, map.remove(REGISTER_IP_KEY), 0, map.get(INTERFACE_KEY), map);
        MetadataUtils.publishServiceDefinition(consumerURL);

        // create service proxy
        return (T) PROXY_FACTORY.getProxy(invoker, ProtocolUtils.isGeneric(generic));
    }
~~~