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
import org.apache.dubbo.rpc.RpcStatus;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ShortestResponseLoadBalance
 * </p>
 * Filter the number of invokers with the shortest response time of success calls and count the weights and quantities of these invokers.
 * If there is only one invoker, use the invoker directly;
 * if there are multiple invokers and the weights are not the same, then random according to the total weight;
 * if there are multiple invokers and the same weight, then randomly called.
 *
 * 根据最优解选择服务提供者
 */
public class ShortestResponseLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "shortestresponse";

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {

        // 可调用的服务提供者的数量
        int length = invokers.size();
        // 初始化一个最短 response 时间
        long shortestResponse = Long.MAX_VALUE;
        // 初始化一个最短 response 总数
        int shortestCount = 0;
        // The index of invokers having the same estimated shortest response time
        int[] shortestIndexes = new int[length];
        // 每个服务提供者的权重
        int[] weights = new int[length];
        // 权重和
        int totalWeight = 0;
        // 调用平均返回时间最短的服务提供者的权重
        int firstWeight = 0;
        // 权重是否相同
        boolean sameWeight = true;

        // 轮询所有的服务提供者
        for (int i = 0; i < length; i++) {
            Invoker<T> invoker = invokers.get(i);
            // 获取服务提供者的状态
            RpcStatus rpcStatus = RpcStatus.getStatus(invoker.getUrl(), invocation.getMethodName());
            // 平均服务调用成功返回时间
            long succeededAverageElapsed = rpcStatus.getSucceededAverageElapsed();
            // 正在活跃的请求数
            int active = rpcStatus.getActive();
            // 此处用平均时间乘以活跃数，获得打分
            // 如果服务提供方很健壮，平均时间很短，但是请求分配的很多，这里分数也会比较高
            // 分数越低，优先级越高
            long estimateResponse = succeededAverageElapsed * active;

            // 获取权重
            int afterWarmup = getWeight(invoker, invocation);
            weights[i] = afterWarmup;

            /**
             * 计算最短数组，shortestResponse 记录当前最短的
             */
            if (estimateResponse < shortestResponse) {
                // 如果当前服务提供者的得分低于最低的得分，则更新最低得分，
                // 并将最优提供者数组的首位置为当前的提供者
                shortestResponse = estimateResponse;
                shortestCount = 1;
                shortestIndexes[0] = i;
                totalWeight = afterWarmup;
                firstWeight = afterWarmup;
                sameWeight = true;
            } else if (estimateResponse == shortestResponse) {
                // 如果相等，则可能存在多个最优解
                shortestIndexes[shortestCount++] = i;
                totalWeight += afterWarmup;
                if (sameWeight && i > 0
                        && afterWarmup != firstWeight) {
                    sameWeight = false;
                }
            }
        }

        // 最优解只有一个的情况，直接选最优解进行调用
        if (shortestCount == 1) {
            return invokers.get(shortestIndexes[0]);
        }
        // 最优解不止一个，且最优解之间的权重不同，那么此处根据权重去随机选择一个
        if (!sameWeight && totalWeight > 0) {
            int offsetWeight = ThreadLocalRandom.current().nextInt(totalWeight);
            for (int i = 0; i < shortestCount; i++) {
                int shortestIndex = shortestIndexes[i];
                offsetWeight -= weights[shortestIndex];
                if (offsetWeight < 0) {
                    return invokers.get(shortestIndex);
                }
            }
        }

        // 最优解不止一个，且权重相同，则随机选择
        return invokers.get(shortestIndexes[ThreadLocalRandom.current().nextInt(shortestCount)]);
    }
}
