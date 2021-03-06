/*
 * Copyright 2018-present yangguo@outlook.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.yangguo.yfs;

import info.yangguo.yfs.common.utils.IdMaker;
import info.yangguo.yfs.config.ClusterConfig;
import info.yangguo.yfs.util.WeightedRoundRobinScheduling;
import lombok.Getter;
import lombok.Setter;
import org.littleshoot.proxy.HostResolver;

import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author:杨果
 * @date:2017/3/31 下午7:28
 * <p>
 * Description:
 */
public class HostResolverImpl implements HostResolver {
    private volatile static HostResolverImpl singleton;
    private ClusterConfig clusterConfig;
    @Getter
    @Setter
    private WeightedRoundRobinScheduling uploadServers;
    @Getter
    @Setter
    private Map<String, WeightedRoundRobinScheduling> downloadServers;

    private HostResolverImpl(ClusterConfig clusterConfig) {
        this.clusterConfig = clusterConfig;
        this.uploadServers = new WeightedRoundRobinScheduling();
        this.downloadServers = new ConcurrentHashMap<>();
    }

    public static HostResolverImpl getSingleton(ClusterConfig clusterConfig) {
        if (singleton == null) {
            synchronized (HostResolverImpl.class) {
                if (singleton == null) {
                    singleton = new HostResolverImpl(clusterConfig);
                }
            }
        }
        return singleton;
    }

    @Override
    public InetSocketAddress resolve(String host, int port)
            throws UnknownHostException {
        WeightedRoundRobinScheduling.Server server = null;
        if (host.equals("upload")) {
            server = uploadServers.getServer();
        } else if (!host.equals("unknown")) {
            String[] hostParts = host.split("-");
            WeightedRoundRobinScheduling weightedRoundRobinScheduling = downloadServers.get(hostParts[0]);
            if (weightedRoundRobinScheduling != null) {
                if (hostParts.length == 1) {
                    server = weightedRoundRobinScheduling.getServer();
                } else if (hostParts.length == 2) {
                    for (WeightedRoundRobinScheduling.Server tmp : weightedRoundRobinScheduling.healthilyServers) {
                        if (tmp.getStoreInfo().getNodeId().equals(hostParts[1])) {
                            server = tmp;
                            break;
                        }
                    }
                    //这样做的目的是防止首次上传节点宕机，在sticky时间内，没服务提供者。
                    if (server == null) {
                        server = weightedRoundRobinScheduling.getServer();
                    }
                }
            }
        }
        if (server != null) {
            return new InetSocketAddress(server.getStoreInfo().getIp(), server.getStoreInfo().getStoreHttpPort());
        } else {
            throw new UnknownHostException("host:" + host + ",port:" + port);
        }
    }
}
