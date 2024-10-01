package com.client.http.config;

import com.client.http.http.GatewayHttpConnection;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BeansDefinitionTest {

    @Mock
    private AppProperties appProperties;

    @Mock
    private JedisCluster jedisCluster;

    @InjectMocks
    private BeansDefinition beansDefinition;

    @Test
    void testSocketSessionCreation() {
        SocketSession socketSession = beansDefinition.socketSession();
        assertNotNull(socketSession);
        assertEquals("gw", socketSession.getType());
    }

    @Test
    void testJedisClusterCreation() {
        when(appProperties.getRedisNodes()).thenReturn(List.of("localhost:6379", "localhost:6380"));
        when(appProperties.getRedisMaxTotal()).thenReturn(10);
        when(appProperties.getRedisMinIdle()).thenReturn(1);
        when(appProperties.getRedisMaxIdle()).thenReturn(5);
        when(appProperties.isRedisBlockWhenExhausted()).thenReturn(true);
        assertNull(beansDefinition.jedisCluster());
    }

    @Test
    void testHttpConnectionManagerList() {
        ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList = beansDefinition.httpConnectionManagerList();
        assertNotNull(httpConnectionManagerList);
        assertTrue(true);
    }

    @Test
    void testServiceProvidersConcurrentHashMap() {
        ConcurrentMap<String, ServiceProvider> serviceProvidersConcurrentHashMap = beansDefinition.serviceProvidersConcurrentHashMap();
        assertNotNull(serviceProvidersConcurrentHashMap);
        assertTrue(true);
    }

    @Test
    void testErrorCodeMappingConcurrentHashMap() {
        ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap = beansDefinition.errorCodeMappingConcurrentHashMap();
        assertNotNull(errorCodeMappingConcurrentHashMap);
        assertTrue(true);
    }

    @Test
    void testRoutingHashMap() {
        ConcurrentMap<Integer, List<RoutingRule>> routingHashMap = beansDefinition.routingHashMap();
        assertNotNull(routingHashMap);
        assertTrue(true);
    }

    @Test
    void testCdrProcessorConfigCreation() {
        assertNotNull(beansDefinition.cdrProcessor(jedisCluster));
    }
}