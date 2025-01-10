package com.client.http.utils;

import lombok.Getter;
import com.paicbd.smsc.utils.Generated;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

@Getter
@Generated
@Component
public class AppProperties {
    // Redis
    @Value("#{'${redis.cluster.nodes}'.split(',')}")
    private List<String> redisNodes;

    @Value("${redis.threadPool.maxTotal:20}")
    private int redisMaxTotal;

    @Value("${redis.threadPool.maxIdle:20}")
    private int redisMaxIdle;

    @Value("${redis.threadPool.minIdle:1}")
    private int redisMinIdle;

    @Value("${redis.threadPool.blockWhenExhausted:true}")
    private boolean redisBlockWhenExhausted;

    @Value("${redis.connection.timeout:0}")
    private int redisConnectionTimeout;

    @Value("${redis.so.timeout:0}")
    private int redisSoTimeout;

    @Value("${redis.maxAttempts:0}")
    private int redisMaxAttempts;

    @Value("${redis.connection.password:}")
    private String redisPassword;

    @Value("${redis.connection.user:}")
    private String redisUser;

    // WebSocket
    @Value("${websocket.server.host:localhost}")
    private String webSocketHost;

    @Value("${websocket.server.port:9000}")
    private int webSocketPort;

    @Value("${websocket.server.path:/ws}")
    private String webSocketPath;

    @Value("${websocket.server.enabled:false}")
    private boolean websocketEnabled;

    @Value("${websocket.header.name:Authorization}")
    private String websocketHeaderName;

    @Value("${websocket.header.value}")
    private String websocketHeaderValue;

    @Value("${websocket.retry.intervalSeconds}")
    private int websocketRetryInterval;

    // Lists
    @Value("${redis.key.gateways}")
    private String keyGatewayRedis;

    @Value("${redis.key.serviceProviders}")
    private String keyServiceProvidersRedis;

    @Value("${redis.key.errorCodeMapping}")
    private String keyErrorCodeMapping;

    @Value("${redis.key.routingRules}")
    private String keyRoutingRules;

    @Value("${application.useHttp2}")
    private boolean http2;

    @Value("${redis.retry.messages.queue}")
    private String retryMessageQueue;

    @Value("${redis.preDeliver.queue}")
    private String preDeliverQueue;

    // Workers per gw
    @Value("${http.workers.per.gw:10}")
    private int httpWorkersPerGw;

    @Value("${http.job.execute.every:1000}")
    private int httpJobExecuteEvery;

    @Value("${http.records.per.gw:1000}")
    private int httpRecordsPerGw;
    
    @Value("${redis.submitSmResult.queue:http_submit_sm_result}")
    private String submitSmResultQueue;
}
