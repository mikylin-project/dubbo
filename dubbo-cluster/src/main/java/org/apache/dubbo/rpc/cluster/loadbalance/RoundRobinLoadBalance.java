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
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {
    public static final String NAME = "roundrobin";

    private static final int RECYCLE_PERIOD = 60000;

    protected static class WeightedRoundRobin {
        private int weight;
        private AtomicLong current = new AtomicLong(0);
        private long lastUpdate;

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }

        public long increaseCurrent() {
            return current.addAndGet(weight);
        }

        public void sel(int total) {
            current.addAndGet(-1 * total);
        }

        public long getLastUpdate() {
            return lastUpdate;
        }

        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();

    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     *
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {

        // key = serviceKey + methodName
        // 这个 key 代表一个 provider 接口
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();

        // 获取权重记录，如果没有的话会创建一个空 map
        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int totalWeight = 0;
        long maxCurrent = Long.MIN_VALUE;
        long now = System.currentTimeMillis();


        Invoker<T> selectedInvoker = null; // 被选中的 provider
        WeightedRoundRobin selectedWRR = null; // 被选中的 provider 的权重 entity
        for (Invoker<T> invoker : invokers) {

            // 此处如果存在权重记录就直接返回，不存在就初始化一个
            // identifyString 是缓存的 key
            String identifyString = invoker.getUrl().toIdentityString();

            /*
                 获取权重的封装对象，如果没有的话会创建一个
                 WeightedRoundRobin 维护两个重要的参数，
                 一个数 current，代表该 provider 当前的调用权重
                 一个是 weight，代表该 provider 恒定的配置权重
             */
            int weight = getWeight(invoker, invocation);
            WeightedRoundRobin weightedRoundRobin = map.computeIfAbsent(identifyString, k -> {
                WeightedRoundRobin wrr = new WeightedRoundRobin();
                wrr.setWeight(weight);
                return wrr;
            });

            // 改权重数据
            if (weight != weightedRoundRobin.getWeight()) {
                weightedRoundRobin.setWeight(weight);
            }

            // cur = weightedRoundRobin.current + weightedRoundRobin.weight
            long cur = weightedRoundRobin.increaseCurrent();
            weightedRoundRobin.setLastUpdate(now);

            // 此处的 cur > maxCurrent，本质上选出了所有 provider 中 current 最大的一个
            // 此处结合上述逻辑，相当于给每个 provider 的 current 增加了一次 weight
            // 并选出了 current 最大的那一个，作为调用方
            if (cur > maxCurrent) {
                maxCurrent = cur;
                selectedInvoker = invoker;
                selectedWRR = weightedRoundRobin;
            }
            totalWeight += weight;
        }


        // 对 map 进行自检
        if (invokers.size() != map.size()) {
            map.entrySet().removeIf(item -> now - item.getValue().getLastUpdate() > RECYCLE_PERIOD);
        }
        if (selectedInvoker != null) {
            // weightedRoundRobin.current = weightedRoundRobin.current - totalWeight
            // 相当于
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }


        /**
         * 上述逻辑简图
         * 假设三个服务 s1,s2,s3 权重均为 10
         *
         * 第一轮叠加权重后的 current：
         * 10 10 10
         * 第一轮选择推送 s1，推送完成后的 current：
         * -20 10 10
         *
         * 第二轮叠加权重后的 current：
         * -10 20 20
         * 第二轮选择推送 s2，推送完成后的 current：
         * -10 -10 20
         *
         * 第三轮叠加权重后的 current：
         * 0 0 30
         * 第三轮选择推送 s3，推送完成后的 current：
         * 0 0 0
         *
         * 第四轮叠加权重后的 current：
         * 10 10 10
         * 第四轮选择推送 s1，推送完成后的 current：
         * -20 10 10
         *
         *
         * 以此类推。
         */

        // 上述代码出问题的情况下默认选第一个
        return invokers.get(0);
    }

}
