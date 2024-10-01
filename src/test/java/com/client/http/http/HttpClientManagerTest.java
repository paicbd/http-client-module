package com.client.http.http;

import com.client.http.utils.AppProperties;
import com.client.http.utils.Constants;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.ServiceProvider;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import redis.clients.jedis.JedisCluster;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpClientManagerTest {

    private final ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ServiceProvider> serviceProvidersConcurrentHashMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, List<RoutingRule>> routingHashMap = new ConcurrentHashMap<>();
    private ListAppender<ILoggingEvent> listAppender;

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @Mock
    private SocketSession socketSession;

    @Mock
    private final CdrProcessor cdrProcessor = new CdrProcessor(jedisCluster);

    @InjectMocks
    private HttpClientManager httpClientManager;

    @BeforeEach
    void setUp() {
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                serviceProvidersConcurrentHashMap, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);

        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(appProperties.getKeyServiceProvidersRedis()).thenReturn("service_providers");
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");

        Logger logger = (Logger) LoggerFactory.getLogger(HttpClientManager.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void startManagerCache_gateway() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Map<String, String> mockGatewaysMap = new HashMap<>();
        String gatewayJson = "{\"name\":\"gwHttp2\",\"password\":\"\",\"ip\":\"thisisaverylongdomainname123.com\",\"port\":0,\"tps\":1,\"network_id\":3,\"system_id\":\"gwHttp2\",\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"STOPPED\",\"active_sessions_numbers\":0,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";
        mockGatewaysMap.put("gwHttp2", gatewayJson);
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenReturn(mockGatewaysMap);

        invokePrivateMethod(httpClientManager, "loadHttpConnectionManager");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("gateways loaded successfully")));
    }

    @Test
    void startManagerCache_gatewayProtocolSMPP() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Map<String, String> mockGatewaysMap = new HashMap<>();
        String gatewayJson = "{\"name\":\"gwHttp2\",\"password\":\"\",\"ip\":\"thisisaverylongdomainname123.com\",\"port\":0,\"tps\":1,\"network_id\":3,\"system_id\":\"gwHttp2\",\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"STOPPED\",\"active_sessions_numbers\":0,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";
        mockGatewaysMap.put("gwHttp2", gatewayJson);
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenReturn(mockGatewaysMap);

        invokePrivateMethod(httpClientManager, "loadHttpConnectionManager");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("gateways loaded successfully")),
                "Expected Gateways load successfully");
    }

    @Test
    void startManagerCache_loadHttpConnectionManager_exception() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis()))
                .thenThrow(new RuntimeException("Simulated Exception"));

        invokePrivateMethod(httpClientManager, "loadHttpConnectionManager");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error on loadHttpConnectionManager: Simulated Exception")),
                "Expected exception message not found in logs");
    }

    @Test
    void startManagerCache_serviceProvider() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Map<String, String> mockServiceProvidersMap = new HashMap<>();
        String serviceProviderJson = "{\"network_id\":1,\"name\":\"spHttp\",\"system_id\":\"spHttp\",\"password\":\"1234\",\"ip\":\"192.168.100.20\",\"port\":7001,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":10,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"500\",\"tps\":10,\"status\":\"STOPPED\",\"enabled\":0,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";
        mockServiceProvidersMap.put("spHttp", serviceProviderJson);
        when(jedisCluster.hgetAll(appProperties.getKeyServiceProvidersRedis())).thenReturn(mockServiceProvidersMap);

        invokePrivateMethod(httpClientManager, "loadServiceProviders");

        ServiceProvider serviceProvider = serviceProvidersConcurrentHashMap.get("spHttp");
        assertNotNull(serviceProvider);
        assertEquals("spHttp", serviceProvider.getSystemId());

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("service providers loaded successfully")));
    }

    @Test
    void startManagerCache_serviceProvider_noProviders() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(jedisCluster.hgetAll(appProperties.getKeyServiceProvidersRedis())).thenReturn(Collections.emptyMap());

        invokePrivateMethod(httpClientManager, "loadServiceProviders");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("No service Providers found on loadServiceProviders")));
    }

    @Test
    void startManagerCache_serviceProvider_exception() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(jedisCluster.hgetAll(appProperties.getKeyServiceProvidersRedis())).thenThrow(new RuntimeException("Redis error"));

        invokePrivateMethod(httpClientManager, "loadServiceProviders");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error on loadServiceProviders: Redis error")));
    }

    @Test
    void startManagerCache_routingRules() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");

        String routingRulesJson = "[{\"id\":1,\"origin_network_id\":1,\"origin_protocol\":\"HTTP\",\"network_id_to_map_sri\":0,\"network_id_to_permanent_failure\":0,\"network_id_temp_failure\":0,\"drop_temp_failure\":false,\"has_filter_rules\":false,\"has_action_rules\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"drop_map_sri\":false,\"new_source_addr\":\"\",\"new_source_addr_ton\":0,\"new_source_addr_npi\":0,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":0,\"new_dest_addr_npi\":0,\"add_source_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"origin_network_type\":\"\",\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"destination\":[]}]";

        Map<String, String> redisRoutingRulesMock = new HashMap<>();
        redisRoutingRulesMock.put("1", routingRulesJson);

        when(jedisCluster.hgetAll("routing_rules")).thenReturn(redisRoutingRulesMock);

        invokePrivateMethod(httpClientManager, "loadRoutingRules");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("routing rules loaded successfully")),
                "Expected success message not found in logs");

        List<RoutingRule> routingRules = routingHashMap.get(1);
        assertNotNull(routingRules);
        assertEquals(1, routingRules.size());
        assertEquals(1, routingRules.get(0).getId());
    }

    @Test
    void startManagerCache_routingRules_withException() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");

        Map<String, String> redisRoutingRulesMock = new HashMap<>();
        redisRoutingRulesMock.put("1", "invalid_json");

        when(jedisCluster.hgetAll("routing_rules")).thenReturn(redisRoutingRulesMock);

        invokePrivateMethod(httpClientManager, "loadRoutingRules");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error loading routing rules")),
                "Expected error message not found in logs");
    }

    @Test
    void startManagerCache_routingRules_exception() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(jedisCluster.hgetAll(appProperties.getKeyRoutingRules()))
                .thenThrow(new RuntimeException("Simulated Exception"));

        invokePrivateMethod(httpClientManager, "loadRoutingRules");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error loading routing rules: Simulated Exception")),
                "Expected exception message not found in logs");
    }

    @Test
    void startManagerCache_loadErrorCodeMapping() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Map<String, String> mockErrorCodeMappingMap = new HashMap<>();
        String errorCodeMappingJson = "[{\"error_code\":100,\"delivery_error_code\":100,\"delivery_status\":\"DELIVRD\"}, {\"error_code\":101,\"delivery_error_code\":101,\"delivery_status\":\"FAILED\"}]";
        mockErrorCodeMappingMap.put("key1", errorCodeMappingJson);

        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping())).thenReturn(mockErrorCodeMappingMap);

        invokePrivateMethod(httpClientManager, "loadErrorCodeMapping");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("error code mapping loaded successfully")),
                "Expected success message not found in logs");

        List<ErrorCodeMapping> errorCodeMappingList = errorCodeMappingConcurrentHashMap.get("key1");
        assertNotNull(errorCodeMappingList);
        assertEquals(2, errorCodeMappingList.size());
        assertEquals(100, errorCodeMappingList.get(0).getErrorCode());
        assertEquals(101, errorCodeMappingList.get(1).getErrorCode());
    }

    @Test
    void startManagerCache_loadErrorCodeMapping_exception() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping()))
                .thenThrow(new RuntimeException("Simulated Exception"));

        invokePrivateMethod(httpClientManager, "loadErrorCodeMapping");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error on loadErrorCodeMapping: Simulated Exception")),
                "Expected exception message not found in logs");
    }

    @Test
    void testUpdateGateway() {
        String gatewayJson = "{\"system_id\":\"gwHttp\",\"protocol\":\"HTTP\"}";
        when(jedisCluster.hget("gateways", "gwHttp")).thenReturn(gatewayJson);

        httpClientManager.updateGateway("gwHttp");

        assertTrue(httpConnectionManagerList.containsKey("gwHttp"), "Gateway should be updated");

        // other protocol
        gatewayJson = "{\"system_id\":\"gwHttp\",\"protocol\":\"SMPP\"}";
        when(jedisCluster.hget("gateways", "gwHttp")).thenReturn(gatewayJson);

        httpClientManager.updateGateway("gwHttp");

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("This gateway")));

        logsList.clear();
        // system_id is null
        httpClientManager.updateGateway(null);

        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No gateways found for connect on method updateGateway")),
                "Expected warning message not found in logs");

        logsList.clear();
        // gateway not found in redis
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(null);

        httpClientManager.updateGateway("gwHttp");

        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("No gateways found for connect on updateGateway")),
                "Expected warning message not found in logs");
    }

    @Test
    void testUpdateGateway_GatewayNull() {
        String invalidGatewayJson = "{\"invalid_json\":\"value\"}";
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(invalidGatewayJson);

        httpClientManager.updateGateway("gwHttp");

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("Gateway not found on redis")),
                "Expected warning message not found in logs");
    }

    @Test
    void testUpdateGateway_SystemIdExistsInConnectionManagerList() {
        String gatewayJson = "{\"system_id\":\"gwHttp\",\"protocol\":\"HTTP\"}";
        when(jedisCluster.hget(anyString(), eq("gwHttp"))).thenReturn(gatewayJson);

        GatewayHttpConnection existingConnection = mock(GatewayHttpConnection.class);
        httpConnectionManagerList.put("gwHttp", existingConnection);

        httpClientManager.updateGateway("gwHttp");

        verify(existingConnection, times(1)).setGateway(any(Gateway.class));
    }

    @Test
    void testConnectGateway() {
        // gateway found
        GatewayHttpConnection gatewayHttpConnection = mock(GatewayHttpConnection.class);
        httpConnectionManagerList.put("gwHttp", gatewayHttpConnection);

        httpClientManager.connectGateway("gwHttp");

        verify(gatewayHttpConnection).connect();
        verify(socketSession).sendStatus("gwHttp", Constants.PARAM_UPDATE_STATUS, "STARTED");

        // gateway not found
        gatewayHttpConnection = mock(GatewayHttpConnection.class);
        httpConnectionManagerList.put("gwHttp", gatewayHttpConnection);

        httpClientManager.connectGateway("gwHttpInvalid");

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("This gateway is not handled by this application")),
                "Expected warning message not found in logs");

        logsList.clear();
        // system_id is null
        httpClientManager.connectGateway(null);

        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No gateways found for connect on method connectGateway")),
                "Expected warning message not found in logs");

        logsList.clear();
        // gateway connection error
        gatewayHttpConnection = mock(GatewayHttpConnection.class);
        httpConnectionManagerList.put("gwHttp", gatewayHttpConnection);

        doThrow(new RuntimeException("Connection failed")).when(gatewayHttpConnection).connect();

        httpClientManager.connectGateway("gwHttp");

        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("Error on connect on gateway gwHttp with error Connection failed")),
                "Expected error message not found in logs");
    }

    @Test
    void testUpdateErrorCodeMapping() {
        String mnoId = "123";
        String errorCodeMappingJson = "[{\"error_code\":100,\"delivery_error_code\":100,\"delivery_status\":\"DELIVRD\"}, {\"error_code\":101,\"delivery_error_code\":101,\"delivery_status\":\"FAILED\"}]";
        when(jedisCluster.hget("error_code_mapping", mnoId)).thenReturn(errorCodeMappingJson);

        httpClientManager.updateErrorCodeMapping(mnoId);

        httpConnectionManagerList.put(mnoId, mock(GatewayHttpConnection.class));
        assertTrue(httpConnectionManagerList.containsKey(mnoId), "Error Code Mapping should be updated");
    }

    @Test
    void testUpdateErrorCodeMapping_nullErrorCodeMappingInRaw_removesMapping() {
        String mnoId = "123";

        String initialErrorCodeMappingJson = "[{\"error_code\":100,\"delivery_error_code\":100,\"delivery_status\":\"DELIVRD\"}, {\"error_code\":101,\"delivery_error_code\":101,\"delivery_status\":\"FAILED\"}]";
        when(jedisCluster.hget(this.appProperties.getKeyErrorCodeMapping(), mnoId)).thenReturn(initialErrorCodeMappingJson);

        httpClientManager.updateErrorCodeMapping(mnoId);

        httpConnectionManagerList.put(mnoId, mock(GatewayHttpConnection.class));

        assertTrue(httpConnectionManagerList.containsKey(mnoId), "Error Code Mapping should be present initially");

        when(jedisCluster.hget(this.appProperties.getKeyErrorCodeMapping(), mnoId)).thenReturn(null);

        httpClientManager.updateErrorCodeMapping(mnoId);

        httpConnectionManagerList.remove(mnoId, mock(GatewayHttpConnection.class));

        assertTrue(httpConnectionManagerList.containsKey(mnoId), "Error Code Mapping should be removed when errorCodeMappingInRaw is null");
    }

    @Test
    void testUpdateErrorCodeMapping_nullMnoId_logsWarning() {
        httpClientManager.updateErrorCodeMapping(null);
        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No Error code mapping found for mnoId null or empty")),
                "Expected warning message not found in logs");
    }

    @Test
    void testDeleteGateway() {
        // gateway not found
        String gatewayJson = "{\"name\":\"gwHttp2\",\"password\":\"\",\"ip\":\"thisisaverylongdomainname123.com\",\"port\":0,\"tps\":1,\"network_id\":3,\"system_id\":\"gwHttp2\",\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"STOPPED\",\"active_sessions_numbers\":0,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";
        when(jedisCluster.hget("gateways", "gwHttp2")).thenReturn(gatewayJson);

        httpClientManager.deleteGateway("gwHttp2");

        assertFalse(httpConnectionManagerList.containsKey("gwHttp"), "Gateway should be deleted");

        // gateway found
        GatewayHttpConnection gatewayHttpConnection = mock(GatewayHttpConnection.class);
        Gateway gateway = new Gateway();
        when(gatewayHttpConnection.getGateway()).thenReturn(gateway);

        httpConnectionManagerList.put("gwHttp2", gatewayHttpConnection);
        httpClientManager.deleteGateway("gwHttp2");

        assertEquals("STOPPED", gateway.getStatus(), "Gateway status should be STOPPED");

        assertFalse(httpConnectionManagerList.containsKey("gwHttp2"), "Gateway should be deleted");
    }

    @Test
    void testUpdateServiceProvider() {
        String serviceProviderJson = "{\"network_id\":1,\"system_id\":\"spHttp\",\"protocol\":\"HTTP\"}";
        when(jedisCluster.hget("service_providers", "spHttp")).thenReturn(serviceProviderJson);

        httpClientManager.updateServiceProvider("spHttp");

        httpConnectionManagerList.put("spHttp", mock(GatewayHttpConnection.class));

        assertTrue(httpConnectionManagerList.containsKey("spHttp"), "Service provider should be updated");
    }

    @Test
    void testUpdateServiceProvider_nullSystemId_logsWarning() {
        httpClientManager.updateServiceProvider(null);

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No service providers found for update on method updateServiceProvider")),
                "Expected warning message not found in logs");
    }

    @Test
    void testUpdateRoutingRules() {
        String routingRulesJson = "[{\"id\":1,\"origin_network_id\":1,\"origin_protocol\":\"HTTP\",\"network_id_to_map_sri\":0,\"network_id_to_permanent_failure\":0,\"network_id_temp_failure\":0,\"drop_temp_failure\":false,\"has_filter_rules\":false,\"has_action_rules\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"drop_map_sri\":false,\"new_source_addr\":\"\",\"new_source_addr_ton\":0,\"new_source_addr_npi\":0,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":0,\"new_dest_addr_npi\":0,\"add_source_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"origin_network_type\":\"\",\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"destination\":[]}]";

        when(jedisCluster.hget("routing_rules", "1")).thenReturn(routingRulesJson);

        httpClientManager.updateRoutingRules("1");

        httpConnectionManagerList.put("1", mock(GatewayHttpConnection.class));

        assertTrue(httpConnectionManagerList.containsKey("1"), "Routing rules should be updated");
    }

    @Test
    void testUpdateRoutingRules_nullId_logsWarning() {
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(null);

        httpClientManager.updateRoutingRules("anyNetworkId");

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("Error trying update routing rule for networkId anyNetworkId")),
                "Expected warning message not found in logs");
    }

    @Test
    void testUpdateServiceProvider_nonHttpProtocol_logsWarning() {
        String serviceProviderJson = "{\"id\":1,\"protocol\":\"SMPP\"}";
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(serviceProviderJson);

        httpClientManager.updateServiceProvider("testSystemId");

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("This service provider testSystemId is not handled by this application. Failed to update")),
                "Expected warning message not found in logs");
    }

    @Test
    void testUpdateServiceProvider_nullServiceProvidersInRaw_logsWarning() {
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(null);

        httpClientManager.updateServiceProvider("testSystemId");

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No service providers found for update on updateServiceProvider")),
                "Expected warning message not found in logs");
    }

    @Test
    void testDeleteRoutingRules() {
        // routing rules found

        httpClientManager.deleteRoutingRules("1");

        assertFalse(httpConnectionManagerList.containsKey(1), "Routing rules should be deleted");

        // routing rules not found
        httpClientManager.deleteRoutingRules("");

        assertTrue(httpConnectionManagerList.isEmpty(), "Routing rules should be empty");

        // routing rules null
        httpClientManager.deleteRoutingRules(null);

        assertTrue(httpConnectionManagerList.isEmpty(), "Routing rules should be null");

        // routing rules exception
        String invalidNetworkId = "invalid_id";

        httpClientManager.deleteRoutingRules(invalidNetworkId);

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Error while deleting routing rules with id invalid_id")),
                "Expected error message not found in logs");
    }

    @Test
    void testStopGateway() {
        // system_id is null
        httpClientManager.stopGateway(null);

        List<ILoggingEvent> logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().equals("No gateways found for stop on method stopGateway")),
                "Expected warning message not found in logs");
        listAppender.list.clear();

        // gateway not found
        String gatewayJson = "{\"name\":\"gwHttp2\",\"password\":\"\",\"ip\":\"thisisaverylongdomainname123.com\",\"port\":0,\"tps\":1,\"network_id\":3,\"system_id\":\"gwHttp2\",\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"STOPPED\",\"active_sessions_numbers\":0,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";
        when(jedisCluster.hget("gateways", "gwHttp2")).thenReturn(gatewayJson);

        httpClientManager.stopGateway("gwHttp2");

        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> {
                            event.getFormattedMessage();
                            return true;
                        }),
                "is not handled by this application");
        listAppender.list.clear();

        // gateway found
        httpConnectionManagerList.put("gwHttp", new GatewayHttpConnection(appProperties, jedisCluster, new Gateway(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), cdrProcessor));

        httpClientManager.stopGateway("gwHttp");

        verify(socketSession).sendStatus("gwHttp", Constants.PARAM_UPDATE_STATUS, "STOPPED");

        listAppender.list.clear();

        // gateway connection error
        GatewayHttpConnection faultyGatewayConnection = mock(GatewayHttpConnection.class);
        when(faultyGatewayConnection.getGateway()).thenThrow(new RuntimeException("Simulated Exception"));
        httpConnectionManagerList.put("faultyGw", faultyGatewayConnection);

        httpClientManager.stopGateway("faultyGw");
        logsList = listAppender.list;

        assertTrue(logsList.stream()
                        .anyMatch(event -> event.getFormattedMessage().contains("Error on stop on gateway faultyGw with error Simulated Exception")),
                "Expected error message not found in logs");
    }

    private void invokePrivateMethod(Object target, String methodName, Object... params) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class<?> clazz = target.getClass();
        Method method = clazz.getDeclaredMethod(methodName, getParameterTypes(params));
        method.setAccessible(true);
        method.invoke(target, params);
    }

    private Class<?>[] getParameterTypes(Object... params) {
        return Arrays.stream(params)
                .map(Object::getClass)
                .toArray(Class<?>[]::new);
    }
}