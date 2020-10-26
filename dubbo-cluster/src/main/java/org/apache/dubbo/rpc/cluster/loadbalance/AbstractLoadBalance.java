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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.LoadBalance;
import java.util.List;
import static org.apache.dubbo.common.constants.CommonConstants.TIMESTAMP_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_KEY;
import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_SERVICE_REFERENCE_PATH;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WARMUP;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WEIGHT;
import static org.apache.dubbo.rpc.cluster.Constants.WARMUP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WEIGHT_KEY;

/**
 * AbstractLoadBalance
 *
 * 负载均衡组件模板
 */
public abstract class AbstractLoadBalance implements LoadBalance {
    /**
     * Calculate the weight according to the uptime proportion of warmup time
     * the new weight will be within 1(inclusive) to weight(inclusive)
     *
     * @param uptime the uptime in milliseconds
     * @param warmup the warmup time in milliseconds
     * @param weight the weight of an invoker
     * @return weight which takes warmup into account
     */
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {

        // ww = (当前时间 - 启动时间) / 预热时间 * 权重
        // 取 ww 和 权重 中的最小值
        // 如果 当前时间 还在 预热时间 内，那么此处 ww 必然小于 权重
        // 如果 当前时间 和 启动时间 相差非常近，或者 预热时间 很长，那么此处 ww 有可能会小于 1，此处会返回 1
        // 如果 当前时间 小于 启动时间，那么是服务的时间问题，ww 就会小于 0，此处会返回 1

        // 从 getWeight(...) 方法可知，此处 ww 必然小于 weight
        int ww = (int) ( uptime / ((float) warmup / weight));
        return ww < 1 ? 1 : (Math.min(ww, weight));
    }

    /**
     * select 方法
     * @param invokers   所有的服务提供者信息的封装
     * @param url        refer url
     * @param invocation invocation.
     * @param <T>
     * @return
     */
    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {

        // 如果没有服务提供者，此处返回 null
        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }

        // 服务的提供者只有一个，直接返回就可以了，没有负载均衡的必要
        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 有多个，那么此处需要不同的策略自行完成具体逻辑
        return doSelect(invokers, url, invocation);
    }

    /**
     * 模板方法，从列表中选择一个 invoker
     *
     * @param invokers 所有的服务提供者信息的封装
     * @param url
     * @param invocation 要发送的信息
     */
    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);


    /**
     * Get the weight of the invoker's invocation which takes warmup time into account
     * if the uptime is within the warmup time, the weight will be reduce proportionally
     *
     * @param invoker    the invoker
     * @param invocation the invocation of this invoker
     * @return weight
     */
    int getWeight(Invoker<?> invoker, Invocation invocation) {
        int weight;

        // 获取 url
        URL url = invoker.getUrl();

        // REGISTRY_SERVICE_REFERENCE_PATH = org.apache.dubbo.registry.RegistryService
        // REGISTRY_KEY = registry
        // WEIGHT_KEY = weight
        // DEFAULT_WEIGHT = 100
        // TIMESTAMP_KEY = timestamp
        // WARMUP_KEY = warmup
        // DEFAULT_WARMUP = 600000

        if (REGISTRY_SERVICE_REFERENCE_PATH.equals(url.getServiceInterface())) {
            // 入参 registry.weight 和 100
            weight = url.getParameter(REGISTRY_KEY + "." + WEIGHT_KEY, DEFAULT_WEIGHT);
        } else {

            // provider 的权重
            weight = url.getMethodParameter(invocation.getMethodName(), WEIGHT_KEY, DEFAULT_WEIGHT);
            if (weight > 0) { // 权重大于 0
                // provider 的启动的时间戳
                long timestamp = invoker.getUrl().getParameter(TIMESTAMP_KEY, 0L);
                if (timestamp > 0L) {
                    long uptime = System.currentTimeMillis() - timestamp;
                    if (uptime < 0) {
                        // 启动的时间戳小于当前时间戳，这种情况可能是存在服务器时间问题
                        // 此处为何返回 1 ？
                        return 1;
                    }

                    // warmup 是预热时间，如果当前时间内，这个 provider 还处于预热当中
                    // 那么就会调用到 calculateWarmupWeight(...) 方法
                    int warmup = invoker.getUrl().getParameter(WARMUP_KEY, DEFAULT_WARMUP);
                    if (uptime > 0 && uptime < warmup) {
                        weight = calculateWarmupWeight((int)uptime, warmup, weight);
                    }
                }
            }
        }

        // 权重不能低于 0
        return Math.max(weight, 0);
    }
}
