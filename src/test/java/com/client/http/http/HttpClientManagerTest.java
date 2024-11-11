package com.client.http.http;

import com.client.http.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpClientManagerTest {
    @Mock
    JedisCluster jedisCluster;
    @Mock
    AppProperties appProperties;
    @Mock
    SocketSession socketSession;
    @Mock
    ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList;
    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingHashMap;
    @Mock
    CdrProcessor cdrProcessor;

    @InjectMocks
    HttpClientManager httpClientManager;

    static Map<String, String> connectionManagerMockBatch = Map.of(
            "4", "{\"name\":\"Receiver\",\"password\":\"1234\",\"ip\":\"192.168.100.18\",\"port\":2778,\"tps\":1,\"network_id\":4,\"system_id\":\"gw1\",\"bind_type\":\"RECEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":true,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"BOUND\",\"active_sessions_numbers\":1,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":false,\"split_smpp_type\":\"TLV\"}",
            "7", "{\"network_id\":7,\"name\":\"httpgw\",\"system_id\":\"httpgw\",\"password\":\"\",\"ip\":\"http://192.168.100.18:9409/gw\",\"port\":0,\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"tps\":1,\"status\":\"STARTED\",\"enabled\":1,\"enquire_link_period\":30000,\"enquire_link_timeout\":0,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"active_sessions_numbers\":0,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}",
            "3", "{\"name\":\"Transmitter\",\"password\":\"1234\",\"ip\":\"192.168.100.18\",\"port\":2778,\"tps\":1,\"network_id\":3,\"system_id\":\"gw1\",\"bind_type\":\"TRANSMITTER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"BOUND\",\"active_sessions_numbers\":1,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"SMPP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":3,\"encoding_gsm7\":0,\"encoding_ucs2\":2,\"split_message\":true,\"split_smpp_type\":\"TLV\"}"
    );

    static Map<String, String> errorCodeMappingMockBatch = Map.of(
            "1", "[{\"error_code\":100,\"delivery_error_code\":55,\"delivery_status\":\"DELIVRD\"}]"
    );

    static Map<String, String> routingRulesMockBatch = Map.of(
            "6", "[{\"id\":3,\"origin_network_id\":6,\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"is_sri_response\":false,\"destination\":[{\"priority\":1,\"network_id\":7,\"dest_protocol\":\"HTTP\",\"network_type\":\"GW\"}],\"new_source_addr\":\"\",\"new_source_addr_ton\":-1,\"new_source_addr_npi\":-1,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":-1,\"new_dest_addr_npi\":-1,\"add_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"drop_map_sri\":false,\"network_id_to_map_sri\":-1,\"network_id_to_permanent_failure\":-1,\"drop_temp_failure\":false,\"network_id_temp_failure\":-1,\"check_sri_response\":false,\"origin_protocol\":\"HTTP\",\"origin_network_type\":\"SP\",\"has_filter_rules\":false,\"has_action_rules\":false}]"
    );

    @Test
    void startManager() {
        assertNotNull(httpClientManager);
        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadHttpConnectionManager() {
        assertDoesNotThrow(() -> httpClientManager.startManager());
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenReturn(connectionManagerMockBatch);

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadHttpConnectionManager_exception() {
        assertDoesNotThrow(() -> httpClientManager.startManager());
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenThrow(new RuntimeException("Simulated Exception"));

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadErrorCodeMapping() {
        assertDoesNotThrow(() -> httpClientManager.startManager());
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping()))
                .thenReturn(errorCodeMappingMockBatch);

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadErrorCodeMapping_exception() {
        assertDoesNotThrow(() -> httpClientManager.startManager());
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping())).thenThrow(new RuntimeException("Simulated Exception"));

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadRoutingRules() {
        assertDoesNotThrow(() -> httpClientManager.startManager());
        var routingHashMapInternal = new ConcurrentHashMap<Integer, List<RoutingRule>>();
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMapInternal, cdrProcessor);

        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hgetAll(appProperties.getKeyRoutingRules()))
                .thenReturn(routingRulesMockBatch);

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void startManagerLoadRoutingRules_exception() {
        assertDoesNotThrow(() -> httpClientManager.startManager());

        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hgetAll(appProperties.getKeyRoutingRules())).thenThrow(new RuntimeException("Simulated Exception"));

        assertDoesNotThrow(() -> httpClientManager.startManager());
    }

    @Test
    void updateGateway() {
        assertDoesNotThrow(() -> httpClientManager.updateGateway("1"));
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "1")).thenReturn(connectionManagerMockBatch.get("1"));

        assertDoesNotThrow(() -> httpClientManager.updateGateway("1"));
    }

    @Test
    void updateGateway_nullStringNetworkId() {
        assertDoesNotThrow(() -> httpClientManager.updateGateway(null));
    }

    @Test
    void updateGateway_noGatewayInRedis() {
        assertDoesNotThrow(() -> httpClientManager.updateGateway("1"));
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "1")).thenReturn(null);

        assertDoesNotThrow(() -> httpClientManager.updateGateway("1"));
    }

    @Test
    void updateGateway_httpProtocol() {
        String gatewayInRaw = connectionManagerMockBatch.get("7");
        assertDoesNotThrow(() -> httpClientManager.updateGateway("7"));
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "7")).thenReturn(gatewayInRaw);

        assertDoesNotThrow(() -> httpClientManager.updateGateway("7"));
    }

    @Test
    void updateGateway_smppProtocol() {
        String gatewayInRaw = connectionManagerMockBatch.get("4");

        assertDoesNotThrow(() -> httpClientManager.updateGateway("4"));
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "1")).thenReturn(gatewayInRaw);

        assertDoesNotThrow(() -> httpClientManager.updateGateway("1"));
    }

    @Test
    void updateGateway_existingInMap() {
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "7")).thenReturn(connectionManagerMockBatch.get("7"));
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        var httpConnectionManagerListInternal = new ConcurrentHashMap<String, GatewayHttpConnection>();
        httpConnectionManagerListInternal.put("7", gatewayHttpConnection);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerListInternal,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);

        assertDoesNotThrow(() -> httpClientManager.updateGateway("7"));
    }

    @Test
    void connectGateway_networkIdNull() {
        assertDoesNotThrow(() -> httpClientManager.connectGateway(null));
    }

    @Test
    void connectGateway_gatewayHttpConnectionNotExist() {
        assertDoesNotThrow(() -> httpClientManager.connectGateway("7"));
    }

    @Test
    void connectGateway_gatewayHttpConnectionExist() {
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        var httpConnectionManagerListInternal = new ConcurrentHashMap<String, GatewayHttpConnection>();
        httpConnectionManagerListInternal.put("7", gatewayHttpConnection);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerListInternal,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);

        assertDoesNotThrow(() -> httpClientManager.connectGateway("7"));
    }

    @Test
    void stopGateway_networkIdNull() {
        assertDoesNotThrow(() -> httpClientManager.stopGateway(null));
    }

    @Test
    void stopGateway_gatewayHttpConnectionNotExist() {
        assertDoesNotThrow(() -> httpClientManager.stopGateway("7"));
    }

    @Test
    void stopGateway_gatewayHttpConnectionExist() {
        var gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, new Gateway(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), cdrProcessor);
        var httpConnectionManagerListInternal = new ConcurrentHashMap<String, GatewayHttpConnection>();
        httpConnectionManagerListInternal.put("7", gatewayHttpConnection);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerListInternal,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);

        assertDoesNotThrow(() -> httpClientManager.stopGateway("7"));
    }

    @Test
    void deleteGateway_networkIdNull() {
        assertDoesNotThrow(() -> httpClientManager.deleteGateway(null));
    }

    @Test
    void deleteGateway_gatewayHttpConnectionNotExist() {
        assertDoesNotThrow(() -> httpClientManager.deleteGateway("7"));
    }

    @Test
    void deleteGateway_gatewayHttpConnectionExist() {
        var gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, new Gateway(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), cdrProcessor);
        var httpConnectionManagerListInternal = new ConcurrentHashMap<String, GatewayHttpConnection>();
        httpConnectionManagerListInternal.put("7", gatewayHttpConnection);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerListInternal,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);

        assertDoesNotThrow(() -> httpClientManager.deleteGateway("7"));
    }

    @Test
    void updateErrorCodeMapping() {
        assertDoesNotThrow(() -> httpClientManager.updateErrorCodeMapping("1"));
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), "1")).thenReturn(errorCodeMappingMockBatch.get("1"));

        assertDoesNotThrow(() -> httpClientManager.updateErrorCodeMapping("1"));
    }

    @Test
    void updateErrorCodeMapping_MnoIdNull() {
        assertDoesNotThrow(() -> httpClientManager.updateErrorCodeMapping(null));
        assertDoesNotThrow(() -> httpClientManager.updateErrorCodeMapping(""));
    }

    @Test
    void updateRoutingRules() {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getKeyRoutingRules(), "6")).thenReturn(routingRulesMockBatch.get("6"));

        assertDoesNotThrow(() -> httpClientManager.updateRoutingRules("6"));
    }

    @Test
    void updateRoutingRules_GettingNull() {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getKeyRoutingRules(), "1")).thenReturn(null);

        assertDoesNotThrow(() -> httpClientManager.updateRoutingRules("1"));
    }

    @Test
    void deleteRoutingRules() {
        var routingHashMapInternal = new ConcurrentHashMap<Integer, List<RoutingRule>>();
        routingHashMapInternal.put(6, List.of(new RoutingRule()));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMapInternal, cdrProcessor);

        assertDoesNotThrow(() -> httpClientManager.deleteRoutingRules("6"));
    }

    @Test
    void deleteRoutingRules_NullParam() {
        assertDoesNotThrow(() -> httpClientManager.deleteRoutingRules(null));
        assertDoesNotThrow(() -> httpClientManager.deleteRoutingRules(""));
    }
}