package com.client.http.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MockitoSettings(strictness = Strictness.LENIENT)
class AppPropertiesTest {

    @InjectMocks
    private AppProperties appProperties;

    @BeforeEach
    void setUp() throws Exception {
        injectField("redisNodes", Arrays.asList("192.168.100.1:6379", "192.168.100.2:6379", "192.168.100.3:6379"));
        injectField("redisMaxTotal", 20);
        injectField("redisMaxIdle", 20);
        injectField("redisMinIdle", 1);
        injectField("redisBlockWhenExhausted", true);
        injectField("webSocketHost", "localhost");
        injectField("webSocketPort", 9976);
        injectField("webSocketPath", "/ws");
        injectField("websocketEnabled", false);
        injectField("websocketHeaderName", "Authorization");
        injectField("websocketHeaderValue", "Authorization");
        injectField("websocketRetryInterval", 5);
        injectField("keyGatewayRedis", "gateway_redis");
        injectField("keyServiceProvidersRedis", "service_providers_redis");
        injectField("keyErrorCodeMapping", "error_code_mapping");
        injectField("keyRoutingRules", "routing_rules");
        injectField("http2", true);
        injectField("retryMessageQueue", "retry_message_queue");
        injectField("preDeliverQueue", "pre_deliver_queue");
        injectField("httpWorkersPerGw", 10);
        injectField("httpJobExecuteEvery", 1000);
        injectField("httpRecordsPerGw", 1000);
        injectField("submitSmResultQueue", "http_submit_sm_result");
    }

    @Test
    void testRedisProperties() {
        List<String> expectedRedisNodes = Arrays.asList("192.168.100.1:6379", "192.168.100.2:6379", "192.168.100.3:6379");
        assertAll("Redis properties",
                () -> assertEquals(expectedRedisNodes, appProperties.getRedisNodes()),
                () -> assertEquals(20, appProperties.getRedisMaxTotal()),
                () -> assertEquals(20, appProperties.getRedisMaxIdle()),
                () -> assertEquals(1, appProperties.getRedisMinIdle()),
                () -> assertTrue(appProperties.isRedisBlockWhenExhausted()),
                () -> assertEquals(10, appProperties.getHttpWorkersPerGw()),
                () -> assertEquals(1000, appProperties.getHttpJobExecuteEvery()),
                () -> assertEquals(1000, appProperties.getHttpRecordsPerGw())
        );
    }

    @Test
    void testWebSocketProperties() {
        assertAll("WebSocket properties",
                () -> assertEquals("localhost", appProperties.getWebSocketHost()),
                () -> assertEquals(9976, appProperties.getWebSocketPort()),
                () -> assertEquals("/ws", appProperties.getWebSocketPath()),
                () -> assertFalse(appProperties.isWebsocketEnabled()),
                () -> assertEquals("Authorization", appProperties.getWebsocketHeaderName()),
                () -> assertEquals("Authorization", appProperties.getWebsocketHeaderValue()),
                () -> assertEquals(5, appProperties.getWebsocketRetryInterval())
        );
    }

    @Test
    void testRedisKeyProperties() {
        assertAll("Redis key properties",
                () -> assertEquals("gateway_redis", appProperties.getKeyGatewayRedis()),
                () -> assertEquals("service_providers_redis", appProperties.getKeyServiceProvidersRedis()),
                () -> assertEquals("error_code_mapping", appProperties.getKeyErrorCodeMapping()),
                () -> assertEquals("routing_rules", appProperties.getKeyRoutingRules()),
                () -> assertTrue(appProperties.isHttp2()),
                () -> assertEquals("retry_message_queue", appProperties.getRetryMessageQueue()),
                () -> assertEquals("pre_deliver_queue", appProperties.getPreDeliverQueue()),
                () -> assertEquals("http_submit_sm_result", appProperties.getSubmitSmResultQueue())
        );
    }

    private void injectField(String fieldName, Object value) throws Exception {
        Field field = AppProperties.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(appProperties, value);
    }
}
