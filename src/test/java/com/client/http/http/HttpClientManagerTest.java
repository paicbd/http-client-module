package com.client.http.http;

import com.client.http.utils.AppProperties;
import com.client.http.utils.Constants;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.RequestDelivery;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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

    @Test
    @DisplayName("HTTP start manager")
    void startManagerWhenLoadContextThenExecutedPostConstruct() {
        HttpClientManager spyHttpClientManager = spy(httpClientManager);
        spyHttpClientManager.startManager();
        verify(spyHttpClientManager).startManager();
    }

    @Test
    @DisplayName("Load HTTP connection")
    void startManagerWhenLoadHttpConnectionThenCheckValues() {
        Gateway httpGW = getHTTPGw();
        String key = String.valueOf(httpGW.getNetworkId());
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenReturn(Map.of(key, httpGW.toString()));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.startManager();

        ArgumentCaptor<GatewayHttpConnection> gatewayCaptor = ArgumentCaptor.forClass(GatewayHttpConnection.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpConnectionManagerList).put(keyCaptor.capture(), gatewayCaptor.capture());
        GatewayHttpConnection httpConnection = gatewayCaptor.getValue();
        assertEquals(httpGW.toString(), httpConnection.getGateway().toString());
        assertEquals(key, keyCaptor.getValue());
    }

    @Test
    @DisplayName("Try to load SMPP connection")
    void startManagerWhenLoadSMPPGatewayThenDoNothing() {
        Gateway smppGW = getSMPPGw01();
        String key = String.valueOf(smppGW.getNetworkId());
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenReturn(Map.of(key, smppGW.toString()));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.startManager();

        verifyNoMoreInteractions(httpConnectionManagerList);
    }

    @Test
    @DisplayName("Try to load HTTP connection")
    void startManagerWhenLoadHttpConnectionThenDoNothing() {
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hgetAll(appProperties.getKeyGatewayRedis())).thenThrow(new RuntimeException("Simulated Exception"));
        httpClientManager.startManager();

        verifyNoMoreInteractions(httpConnectionManagerList);
    }

    @Test
    @DisplayName("Load error codes mapping")
    void startManagerWhenLoadErrorCodeMappingThenCheckValues() {
        String key = "1";
        List<ErrorCodeMapping> errorCodeMappings = getErrorCodeMappingList();
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping()))
                .thenReturn(Map.of(key, Converter.valueAsString(errorCodeMappings)));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.startManager();
        ArgumentCaptor<List<ErrorCodeMapping>> listErrorCodeCaptor = getArgumentCaptureList();
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(errorCodeMappingConcurrentHashMap).put(keyCaptor.capture(), listErrorCodeCaptor.capture());
        assertEquals(key, keyCaptor.getValue());
    }

    @Test
    @DisplayName("Try to load error codes mapping")
    void startManagerWhenLoadErrorCodeMappingThenDoNothing() {
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hgetAll(appProperties.getKeyErrorCodeMapping())).thenThrow(new RuntimeException("Simulated Exception"));
        httpClientManager.startManager();
        verifyNoMoreInteractions(errorCodeMappingConcurrentHashMap);
    }

    @Test
    @DisplayName("Load routing rules")
    void startManagerWhenLoadRoutingRulesThenCheckValues() {
        String key = "6";
        List<RoutingRule> routingRules = getRoutingRuleList();
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hgetAll(appProperties.getKeyRoutingRules())).thenReturn(Map.of(key, Converter.valueAsString(routingRules)));
        var routingHashMapInternal = new ConcurrentHashMap<Integer, List<RoutingRule>>();
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList, errorCodeMappingConcurrentHashMap, routingHashMapInternal, cdrProcessor);
        httpClientManager.startManager();
        assertNotNull(routingHashMapInternal.get(Integer.parseInt(key)));
        assertEquals(routingRules.size(), routingHashMapInternal.get(Integer.parseInt(key)).size());
    }

    @Test
    @DisplayName("Try to load routing rules")
    void startManagerWhenLoadRoutingRulesThenDoNothing() {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hgetAll(appProperties.getKeyRoutingRules())).thenThrow(new RuntimeException("Simulated Exception"));
        httpClientManager.startManager();
        verifyNoMoreInteractions(routingHashMap);
    }

    @Test
    @DisplayName("Try to update gateway and the networkId not found")
    void updateGatewayWhenGatewayNotFoundThenDoNothing() {
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), "1")).thenReturn(null);
        httpClientManager.updateGateway("1");

        verifyNoMoreInteractions(httpConnectionManagerList);
    }

    @Test
    @DisplayName("Try to update gateway when protocol isn't HTTP")
    void updateGatewayWhenGatewayProtocolIsNotHTTPThenDoNothing() {
        Gateway httpGW = getHTTPGw();
        httpGW.setName("httpgw01");
        httpGW.setProtocol("smpp");
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), String.valueOf(httpGW.getNetworkId()))).thenReturn(httpGW.toString());

        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateGateway(String.valueOf(httpGW.getNetworkId()));

        verify(httpConnectionManagerList, never()).containsKey(String.valueOf(httpGW.getNetworkId()));
    }

    @Test
    @DisplayName("Update HTTP gateway when not in the connection manager")
    void updateGatewayWhenGatewayProtocolNotInManagerListHTTPThenCheckValues() {
        Gateway httpGW = getHTTPGw();
        httpGW.setName("httpgw01");
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), String.valueOf(httpGW.getNetworkId()))).thenReturn(httpGW.toString());

        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateGateway(String.valueOf(httpGW.getNetworkId()));

        ArgumentCaptor<GatewayHttpConnection> gatewayCaptor = ArgumentCaptor.forClass(GatewayHttpConnection.class);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpConnectionManagerList).put(keyCaptor.capture(), gatewayCaptor.capture());
        GatewayHttpConnection httpConnection = gatewayCaptor.getValue();
        assertEquals(httpGW.getName(), httpConnection.getGateway().getName());
        assertEquals(String.valueOf(httpGW.getNetworkId()), keyCaptor.getValue());
    }

    @Test
    @DisplayName("Update HTTP gateway when exist in the connection manager")
    void updateGatewayWhenGatewayProtocolInManagerListHTTPThenCheckValues() {
        Gateway httpGW = getHTTPGw();
        String key = String.valueOf(httpGW.getNetworkId());
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(appProperties.getKeyGatewayRedis(), key)).thenReturn(httpGW.toString());
        when(httpConnectionManagerList.containsKey(key)).thenReturn(true);
        when(httpConnectionManagerList.get(key)).thenReturn(gatewayHttpConnection);

        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateGateway(key);

        ArgumentCaptor<Gateway> gatewayCaptor = ArgumentCaptor.forClass(Gateway.class);
        verify(gatewayHttpConnection).setGateway(gatewayCaptor.capture());
        assertEquals(httpGW.toString(), gatewayCaptor.getValue().toString());
    }

    @Test
    @DisplayName("Try to connect gateway when not in the connection manager")
    void connectGatewayWhenGatewayNotInConnectionManagerThenDoNothing() {
        String key = "7";
        when(httpConnectionManagerList.get(key)).thenReturn(null);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.connectGateway(key);
        verifyNoMoreInteractions(httpConnectionManagerList);
    }

    @Test
    @DisplayName("Connect gateway when exist in the connection manager")
    void connectGatewayWhenGatewayInConnectionManagerThenCheckConnect() {
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        String key = "7";
        when(httpConnectionManagerList.get(key)).thenReturn(gatewayHttpConnection);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.connectGateway(key);
        ArgumentCaptor<String> networkCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusParamCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(gatewayHttpConnection).connect();
        verify(socketSession).sendStatus(networkCaptor.capture(), statusParamCaptor.capture(), statusCaptor.capture());
        assertEquals(key, networkCaptor.getValue());
        assertEquals(Constants.PARAM_UPDATE_STATUS, statusParamCaptor.getValue());
        assertEquals("STARTED", statusCaptor.getValue());
    }

    @Test
    @DisplayName("Try to stop gateway when not in the connection manager")
    void stopGatewayWhenGatewayNotInConnectionManagerThenDoNothing() {
        String key = "7";
        when(httpConnectionManagerList.get(key)).thenReturn(null);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.stopGateway(key);
        verifyNoMoreInteractions(socketSession);
    }

    @Test
    @DisplayName("Stop gateway when exist in the connection manager")
    void stopGatewayWhenGatewayInConnectionManagerThenCheckValues() {
        String key = "7";
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        var gateway = mock(Gateway.class);
        when(httpConnectionManagerList.get(key)).thenReturn(gatewayHttpConnection);
        when(gatewayHttpConnection.getGateway()).thenReturn(gateway);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.stopGateway(key);
        ArgumentCaptor<String> networkCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusParamCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(socketSession).sendStatus(networkCaptor.capture(), statusParamCaptor.capture(), statusCaptor.capture());
        assertEquals(key, networkCaptor.getValue());
        assertEquals(Constants.PARAM_UPDATE_STATUS, statusParamCaptor.getValue());
        assertEquals(Constants.STOPPED, statusCaptor.getValue());
    }

    @Test
    @DisplayName("Try to delete gateway when not in connection manager")
    void deleteGatewayWhenGatewayNotInConnectionManagerThenDoNothing() {
        String key = "7";
        when(httpConnectionManagerList.get(key)).thenReturn(null);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.deleteGateway(key);
        verifyNoMoreInteractions(httpConnectionManagerList);
    }

    @Test
    @DisplayName("Delete gateway when exist in the connection manager")
    void deleteGatewayWhenGatewayInConnectionManagerThenCheckValues() {
        String key = "7";
        var gatewayHttpConnection = mock(GatewayHttpConnection.class);
        var gateway = mock(Gateway.class);
        when(httpConnectionManagerList.get(key)).thenReturn(gatewayHttpConnection);
        when(gatewayHttpConnection.getGateway()).thenReturn(gateway);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.deleteGateway(key);
        verify(httpConnectionManagerList).remove(key);
    }

    @Test
    @DisplayName("Try to update error code mapping when raw is null")
    void updateErrorCodeMappingWhenRawIsNullThenDoNothing() {
        String key = "1";
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), key)).thenReturn(null);
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateErrorCodeMapping(key);
        verify(errorCodeMappingConcurrentHashMap).remove(key);
        assertEquals(0, errorCodeMappingConcurrentHashMap.size());
    }

    @Test
    @DisplayName("Update error code mapping when exist in redis")
    void updateErrorCodeMappingWhenInRedisThenCheckUpdate() {
        String key = "1";
        List<ErrorCodeMapping> errorCodeMappings = getErrorCodeMappingList();
        when(appProperties.getKeyErrorCodeMapping()).thenReturn("error_code_mapping");
        when(jedisCluster.hget(appProperties.getKeyErrorCodeMapping(), key)).thenReturn(Converter.valueAsString(errorCodeMappings));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateErrorCodeMapping(key);
        ArgumentCaptor<String> mnoCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<ErrorCodeMapping>> listArgumentCaptor = getArgumentCaptureList();
        verify(errorCodeMappingConcurrentHashMap).put(mnoCaptor.capture(), listArgumentCaptor.capture());
        assertEquals(key, mnoCaptor.getValue());
        assertEquals(errorCodeMappings.size(), listArgumentCaptor.getValue().size());
    }

    @Test
    @DisplayName("Update error code mapping when the mno is empty")
    void updateErrorCodeMappingWhenMnoIdIsEmptyThenDoNothing() {
        httpClientManager.updateErrorCodeMapping("");
        verifyNoMoreInteractions(jedisCluster);
    }

    @Test
    @DisplayName("Update routing rules when exist in redis")
    void updateRoutingRulesWhenExistInRedisThenCheckValues() {
        String key = "6";
        List<RoutingRule> routingRules = getRoutingRuleList();
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getKeyRoutingRules(), key)).thenReturn(Converter.valueAsString(routingRules));
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.updateRoutingRules(key);

        ArgumentCaptor<Integer> keyCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<List<RoutingRule>> listArgumentCaptor = getArgumentCaptureList();
        verify(routingHashMap).put(keyCaptor.capture(), listArgumentCaptor.capture());
        assertEquals(Integer.parseInt(key), keyCaptor.getValue());
        assertEquals(routingRules.size(), listArgumentCaptor.getValue().size());
    }

    @Test
    @DisplayName("Update routing rules when getting is null")
    void updateRoutingRulesWhenGettingIsNullThenDoNothing() {
        when(appProperties.getKeyRoutingRules()).thenReturn("routing_rules");
        when(jedisCluster.hget(appProperties.getKeyRoutingRules(), "1")).thenReturn(null);
        httpClientManager.updateRoutingRules("1");
        verifyNoMoreInteractions(routingHashMap);
    }

    @Test
    @DisplayName("Delete routing rules when exists in redis")
    void deleteRoutingRulesWhenExistInRedisThenCheckDelete() {
        String key = "6";
        httpClientManager = new HttpClientManager(jedisCluster, appProperties, socketSession, httpConnectionManagerList,
                errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        httpClientManager.deleteRoutingRules(key);

        ArgumentCaptor<Integer> keyCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(routingHashMap).remove(keyCaptor.capture());
        assertEquals(Integer.parseInt(key), keyCaptor.getValue());
    }

    private static Gateway getHTTPGw() {
        return Gateway.builder()
                .networkId(7)
                .name("httpgw")
                .systemId("httpgw")
                .password("1234")
                .ip("192.168.100.18")
                .port(9409)
                .bindType("TRANSCEIVER")
                .systemType("")
                .interfaceVersion("IF_50")
                .sessionsNumber(1)
                .addressTON(1)
                .addressNPI(4)
                .addressRange("")
                .tps(1)
                .status("STARTED")
                .enabled(1)
                .enquireLinkPeriod(30000)
                .enquireLinkTimeout(0)
                .requestDLR(RequestDelivery.NON_REQUEST_DLR.getValue())
                .noRetryErrorCode("")
                .retryAlternateDestinationErrorCode("")
                .bindTimeout(5000)
                .bindRetryPeriod(10000)
                .pduTimeout(5000)
                .pduProcessorDegree(1)
                .threadPoolSize(100)
                .mno(1)
                .tlvMessageReceiptId(false)
                .sessionsNumber(0)
                .protocol("HTTP")
                .autoRetryErrorCode("")
                .encodingIso88591(3)
                .encodingGsm7(1)
                .encodingUcs2(2)
                .splitMessage(false)
                .splitSmppType("TLV")
                .build();
    }

    private static Gateway getSMPPGw01() {
        return Gateway.builder()
                .name("Receiver")
                .password("1234")
                .ip("192.168.100.18")
                .port(2778)
                .tps(1)
                .networkId(4)
                .systemId("gw1")
                .bindType("RECEIVER")
                .systemType("")
                .interfaceVersion("IF_50")
                .sessionsNumber(1)
                .addressTON(1)
                .addressNPI(4)
                .addressRange("")
                .enabled(0)
                .enquireLinkPeriod(30000)
                .requestDLR(RequestDelivery.REQUEST_DLR.getValue())
                .noRetryErrorCode("")
                .retryAlternateDestinationErrorCode("")
                .bindTimeout(5000)
                .bindRetryPeriod(10000)
                .pduTimeout(5000)
                .pduProcessorDegree(1)
                .threadPoolSize(100)
                .mno(1)
                .status("BOUND")
                .enquireLinkTimeout(0)
                .tlvMessageReceiptId(false)
                .messageIdDecimalFormat(false)
                .protocol("SMPP")
                .autoRetryErrorCode("")
                .encodingIso88591(3)
                .encodingGsm7(0)
                .encodingUcs2(2)
                .splitMessage(false)
                .splitSmppType("TLV")
                .build();
    }

    private static List<ErrorCodeMapping> getErrorCodeMappingList() {
        return List.of(ErrorCodeMapping.builder().errorCode(100).deliveryErrorCode(55).deliveryStatus("DELIVRD").build());
    }

    private static List<RoutingRule> getRoutingRuleList() {
        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setPriority(1);
        destination.setNetworkId(6);
        destination.setProtocol("HTTP");
        destination.setNetworkType("GW");
        return List.of(
                RoutingRule.builder()
                        .id(3)
                        .originNetworkId(6)
                        .sriResponse(false)
                        .destination(List.of(destination))
                        .newSourceAddrTon(-1)
                        .newSourceAddrNpi(-1)
                        .newDestAddrTon(-1)
                        .newDestAddrNpi(-1)
                        .dropMapSri(false)
                        .networkIdToMapSri(-1)
                        .networkIdToPermanentFailure(-1)
                        .dropTempFailure(false)
                        .networkIdTempFailure(-1)
                        .checkSriResponse(false)
                        .originProtocol("HTTP")
                        .originNetworkType("SP")
                        .hasFilterRules(false)
                        .hasActionRules(false)
                        .build()
        );
    }

    @SuppressWarnings("unchecked")
    private static <U> ArgumentCaptor<U> getArgumentCaptureList() {
        return (ArgumentCaptor<U>) ArgumentCaptor.forClass(List.class);
    }
}