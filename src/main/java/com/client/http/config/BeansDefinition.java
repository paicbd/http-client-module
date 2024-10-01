package com.client.http.config;

import com.client.http.http.GatewayHttpConnection;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Configuration
@RequiredArgsConstructor
public class BeansDefinition {
    private final AppProperties appProperties;

    @Bean
    public JedisCluster jedisCluster() {
        return Converter.paramsToJedisCluster(getJedisClusterParams(appProperties.getRedisNodes(), appProperties.getRedisMaxTotal(),
                appProperties.getRedisMinIdle(), appProperties.getRedisMaxIdle(), appProperties.isRedisBlockWhenExhausted()));
    }

    @Bean
    public SocketSession socketSession() {
        return new SocketSession("gw"); // Gateways
    }

    @Bean
    public ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList() { return new ConcurrentHashMap<>(); }

    @Bean
    public ConcurrentMap<String, ServiceProvider> serviceProvidersConcurrentHashMap() { return new ConcurrentHashMap<>(); }

    @Bean
    public ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap() { return new ConcurrentHashMap<>(); }

    @Bean
    public ConcurrentMap<Integer, List<RoutingRule>> routingHashMap() {
        return new ConcurrentHashMap<>();
    }

    private UtilsRecords.JedisConfigParams getJedisClusterParams(List<String> nodes, int maxTotal, int minIdle, int maxIdle, boolean blockWhenExhausted) {
        return new UtilsRecords.JedisConfigParams(nodes, maxTotal, minIdle, maxIdle, blockWhenExhausted);
    }

    @Bean
    public CdrProcessor cdrProcessor(JedisCluster jedisCluster) {
        return new CdrProcessor(jedisCluster);
    }
}

