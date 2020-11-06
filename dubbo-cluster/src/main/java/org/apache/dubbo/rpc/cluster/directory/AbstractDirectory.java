/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.cluster.directory;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.Directory;
import org.apache.dubbo.rpc.cluster.Router;
import org.apache.dubbo.rpc.cluster.RouterChain;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.apache.dubbo.common.constants.CommonConstants.DUBBO;
import static org.apache.dubbo.common.constants.CommonConstants.INTERFACE_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.MONITOR_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PATH_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROTOCOL_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.REFER_KEY;

/**
 * Abstract implementation of Directory: Invoker list returned from this Directory's list method have been filtered by Routers
 *
 * 字典模板
 */
public abstract class AbstractDirectory<T> implements Directory<T> {

    // logger
    private static final Logger logger = LoggerFactory.getLogger(AbstractDirectory.class);

    private final URL url;

    private volatile boolean destroyed = false;

    private volatile URL consumerUrl;

    protected final Map<String, String> queryMap; // Initialization at construction time, assertion not null
    protected final String consumedProtocol;

    protected RouterChain<T> routerChain;

    public AbstractDirectory(URL url) {
        this(url, null);
    }

    public AbstractDirectory(URL url, RouterChain<T> routerChain) {
        if (url == null) {
            throw new IllegalArgumentException("url == null");
        }

        /**
         * REFER_KEY = "refer"
         * PATH_KEY = "path"
         * PROTOCOL_KEY = "protocol"
         * MONITOR_KEY = "monitor"
         * INTERFACE_KEY = "interface"
         * DUBBO = "dubbo"
         */

        // 获取 url 里的参数
        queryMap = StringUtils.parseQueryString(url.getParameterAndDecoded(REFER_KEY));

        String path = queryMap.get(PATH_KEY);

        // 协议，如果协议缺省则为 dubbo
        this.consumedProtocol = this.queryMap.get(PROTOCOL_KEY) == null ? DUBBO : this.queryMap.get(PROTOCOL_KEY);

        // 将初始的 URL 删掉部分 param 之后获得的一个新的 URL 对象
        // 此处的 url 是 new 出来的，并不是原来的对象
        this.url = url.removeParameter(REFER_KEY).removeParameter(MONITOR_KEY);

        // 同理
        this.consumerUrl = this.url.setProtocol(consumedProtocol).setPath(path == null ? queryMap.get(INTERFACE_KEY) : path).addParameters(queryMap)
                .removeParameter(MONITOR_KEY);

        setRouterChain(routerChain);
    }

    @Override
    public List<Invoker<T>> list(Invocation invocation) throws RpcException {

        // 关闭状态下抛出异常
        if (destroyed) {
            throw new RpcException("Directory already destroyed .url: " + getUrl());
        }

        return doList(invocation);
    }

    @Override
    public URL getUrl() {
        return url;
    }

    public RouterChain<T> getRouterChain() {
        return routerChain;
    }

    public void setRouterChain(RouterChain<T> routerChain) {
        this.routerChain = routerChain;
    }

    protected void addRouters(List<Router> routers) {
        routers = routers == null ? Collections.emptyList() : routers;
        routerChain.addRouters(routers);
    }

    public URL getConsumerUrl() {
        return consumerUrl;
    }

    public void setConsumerUrl(URL consumerUrl) {
        this.consumerUrl = consumerUrl;
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public void destroy() {
        destroyed = true;
    }

    @Override
    public void discordAddresses() {
        // do nothing by default
    }

    /**
     * 模板方法
     */
    protected abstract List<Invoker<T>> doList(Invocation invocation) throws RpcException;

}
