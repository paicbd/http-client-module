package com.client.http.http;

import com.client.http.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.MessagePart;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.RequestDelivery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class GatewayHttpConnectionTest {

    @Mock
    ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;

    @Mock
    ConcurrentMap<Integer, List<RoutingRule>> routingHashMap;

    @Mock
    AppProperties appProperties;

    @Mock
    JedisCluster jedisCluster;

    @Mock
    private Gateway gateway;

    @Mock
    CdrProcessor cdrProcessor;

    GatewayHttpConnection gatewayHttpConnection;

    @Mock
    HttpResponse<String> response;

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    @DisplayName("Send message and response not contains the message id")
    void sendMessageWhenValidateRegisteredDeliveryThenDoNothing(int registeredDelivery) {
        Gateway httpGW = getHTTPGw();
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setRegisteredDelivery(registeredDelivery);
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(2);
        verify(jedisCluster, never()).hset(eq("http_submit_sm_result"), anyString(), anyString());
    }

    @Test
    @DisplayName("Send message when message parts is not null")
    void sendMessageWhenMessagePartsIsNotNullThenDoNothing() {
        Gateway httpGW = getHTTPGw();
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        String messageId = "1719421854353-11028072268459";
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setEsmClass(64);
        messageEvent.setMessageParts(
                List.of(
                        MessagePart.builder()
                                .messageId(messageId)
                                .shortMessage("Testing message part I'm the first part")
                                .segmentSequence(1)
                                .totalSegment(2)
                                .msgReferenceNumber("2")
                                .udhJson("{\"message\":\"Testing message part I'm the first part\",\"0x00\":[2,2,1]}")
                                .build(),
                        MessagePart.builder()
                                .messageId(messageId)
                                .shortMessage("Testing message part I'm the second part")
                                .segmentSequence(2)
                                .totalSegment(2)
                                .msgReferenceNumber("2")
                                .udhJson("{\"message\":\"Testing message part I'm the second part\",\"0x00\":[2,2,2]}")
                                .build()
                )
        );
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(2);
        verify(jedisCluster, never()).hset(eq("http_submit_sm_result"), anyString(), anyString());
    }

    @ParameterizedTest
    @MethodSource("handlerErrorParameters")
    @DisplayName("Error handler when throws HttpTimeoutException")
    void globalErrorHandlerWhenThrowsExceptionThenSendToAutoRetryProcess(int validityPeriod, boolean lastRetry, String originProtocol) {
        Gateway httpGW = getHTTPGw();
        httpGW.setPduTimeout(100);
        httpGW.setIp("http://18.224.164.86:3000/api/callback");
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.isHttp2()).thenReturn(true);
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setValidityPeriod(validityPeriod);
        messageEvent.setLastRetry(lastRetry);
        messageEvent.setOriginProtocol(originProtocol);
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(4);

        if (!originProtocol.isEmpty()) {
            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
            verify(jedisCluster, atLeastOnce()).rpush(keyCaptor.capture(), valueCaptor.capture());
            MessageEvent deliverSmEvent = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
            assertEquals(originProtocol + "_dlr", keyCaptor.getValue());
            assertEquals(messageEvent.getMessageId(), deliverSmEvent.getMessageId());
        } else {
            verify(jedisCluster, never()).rpush(eq(originProtocol), anyString());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 408})
    @DisplayName("Handle no retry error")
    void handleNoRetryErrorWhenErrorInNoRetryListThenCheckValues(int errorCode) {
        Gateway httpGW = getHTTPGw();
        httpGW.setPduTimeout(100);
        httpGW.setIp("http://18.224.164.86:3000/api/callback");
        httpGW.setNoRetryErrorCode("408");
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.isHttp2()).thenReturn(true);
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        MessageEvent messageEvent = getMessageEvent();
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        List<ErrorCodeMapping> errorCodeMappings = getErrorCodeMappingList(errorCode);
        when(errorCodeMappingConcurrentHashMap.get(String.valueOf(httpGW.getMno()))).thenReturn(errorCodeMappings);
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(2);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).rpush(keyCaptor.capture(), valueCaptor.capture());
        MessageEvent deliverSmEvent = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
        assertEquals("http_dlr", keyCaptor.getValue());
        assertEquals(messageEvent.getMessageId(), deliverSmEvent.getMessageId());
    }

    @ParameterizedTest
    @MethodSource("alternateDestinationParameters")
    @DisplayName("Handle retry alternate destination")
    void handleRetryAlternateDestinationWhenErrorInAlternateDestinationListThen(List<RoutingRule> routingRules, String destinationProtocol, String retryDestNetworkId) {
        Gateway httpGW = getHTTPGw();
        httpGW.setPduTimeout(100);
        httpGW.setIp("http://18.224.164.86:3000/api/callback");
        httpGW.setRetryAlternateDestinationErrorCode("408");
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.isHttp2()).thenReturn(true);
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        MessageEvent messageEvent = getMessageEvent();
        messageEvent.setRetryDestNetworkId(retryDestNetworkId);
        messageEvent.setDestNetworkId(4);
        messageEvent.setShortMessage("Short message example");
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        when(routingHashMap.get(messageEvent.getOriginNetworkId())).thenReturn(routingRules);
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(3);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).rpush(keyCaptor.capture(), valueCaptor.capture());
        MessageEvent submitSmEventToRetry = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);

        if (Objects.isNull(routingRules) || routingRules.isEmpty() || destinationProtocol.isEmpty()) {
            assertEquals("http_dlr", keyCaptor.getValue());
            assertEquals(messageEvent.getMessageId(), submitSmEventToRetry.getMessageId());
        } else {
            assertEquals(messageEvent.getOriginNetworkId() + "_" + destinationProtocol + "_message", keyCaptor.getValue());
            assertEquals(messageEvent.getMessageId(), submitSmEventToRetry.getMessageId());
            assertEquals("5,4", submitSmEventToRetry.getRetryDestNetworkId());
            assertTrue(submitSmEventToRetry.isRetry());
        }
    }

    @Test
    @DisplayName("Handle auto retry")
    void handleAutoRetryWhenErrorInAutoRetryListThenCheckValues() {
        Gateway httpGW = getHTTPGw();
        httpGW.setPduTimeout(100);
        httpGW.setIp("http://18.224.164.86:3000/api/callback");
        httpGW.setAutoRetryErrorCode("408");
        String key = httpGW.getNetworkId() + "_http_message";
        when(appProperties.isHttp2()).thenReturn(true);
        when(appProperties.getHttpWorkersPerGw()).thenReturn(1);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(key)).thenReturn(1L);
        when(appProperties.getRetryMessageQueue()).thenReturn("sms_retry");
        MessageEvent messageEvent = getMessageEvent();
        List<String> mockBatch = List.of(messageEvent.toString());
        when(jedisCluster.lpop(key, 1)).thenReturn(mockBatch).thenAnswer(invocationOnMock -> List.of());
        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, httpGW, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        gatewayHttpConnection.connect();

        toSleep(3);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster, atLeastOnce()).lpush(keyCaptor.capture(), valueCaptor.capture());
        MessageEvent submitSmEventToRetry = Converter.stringToObject(valueCaptor.getValue(), MessageEvent.class);
        assertEquals(appProperties.getRetryMessageQueue(), keyCaptor.getValue());
        assertEquals(messageEvent.getMessageId(), submitSmEventToRetry.getMessageId());
        assertEquals(1, submitSmEventToRetry.getRetryNumber());
    }

    @Test
    @DisplayName("Add submit sm response in cache")
    void addInCacheWhenRegisteredDeliveryIsNotZeroThenCheckValues() throws Exception {
        MessageEvent messageEvent = MessageEvent.builder().messageId("1719421854355-110280722684595").build();
        when(response.body()).thenReturn(messageEvent.toString());
        when(appProperties.getSubmitSmResultQueue()).thenReturn("http_submit_sm_result");

        MessageEvent submitSmEvent = getMessageEvent();

        gatewayHttpConnection = new GatewayHttpConnection(appProperties, jedisCluster, gateway, errorCodeMappingConcurrentHashMap, routingHashMap, cdrProcessor);
        invokePrivateMethod(gatewayHttpConnection, new Class<?>[]{MessageEvent.class, HttpResponse.class}, submitSmEvent, response);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> fieldCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> smResponseCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedisCluster).hset(keyCaptor.capture(), fieldCaptor.capture(), smResponseCaptor.capture());

        assertEquals(appProperties.getSubmitSmResultQueue(), keyCaptor.getValue());
        assertEquals(messageEvent.getMessageId(), fieldCaptor.getValue());
        SubmitSmResponseEvent submitSmResponseEvent = Converter.stringToObject(smResponseCaptor.getValue(), SubmitSmResponseEvent.class);
        assertEquals(messageEvent.getMessageId(), submitSmResponseEvent.getHashId());
        assertEquals(submitSmEvent.getSystemId(), submitSmResponseEvent.getSystemId());
    }

    private static Stream<Arguments> handlerErrorParameters() {
        return Stream.of(
                Arguments.of(0, false, "http"),
                Arguments.of(320, true, "smpp"),
                Arguments.of(0, false, "")
        );
    }

    private static Stream<Arguments> alternateDestinationParameters() {
        return Stream.of(
                Arguments.of(getRoutingRuleList("HTTP"), "http", "5"),
                Arguments.of(getRoutingRuleList("SMPP"), "smpp", "5"),
                Arguments.of(getRoutingRuleList("SS7"), "ss7", "5"),
                Arguments.of(getRoutingRuleList(""), "", "")
        );
    }

    private static void invokePrivateMethod(Object targetObject, Class<?>[] parameterTypes, Object... parameters)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = targetObject.getClass().getDeclaredMethod("addInCache", parameterTypes);
        method.setAccessible(true);
        method.invoke(targetObject, parameters);
    }

    private static Gateway getHTTPGw() {
        return Gateway.builder()
                .networkId(3)
                .name("httpgw")
                .systemId("httpgw")
                .password("1234")
                .ip("http://18.224.164.85:3000/api/callback")
                .port(9409)
                .bindType("TRANSCEIVER")
                .systemType("")
                .interfaceVersion("IF_50")
                .sessionsNumber(1)
                .addressTON(1)
                .addressNPI(4)
                .addressRange("")
                .tps(1)
                .status("STOPPED")
                .enabled(0)
                .enquireLinkPeriod(30000)
                .enquireLinkTimeout(0)
                .requestDLR(RequestDelivery.NON_REQUEST_DLR.getValue())
                .noRetryErrorCode("400, 500")
                .retryAlternateDestinationErrorCode("502")
                .bindTimeout(5000)
                .bindRetryPeriod(10000)
                .pduTimeout(5000)
                .pduProcessorDegree(1)
                .threadPoolSize(100)
                .mno(1)
                .tlvMessageReceiptId(false)
                .sessionsNumber(0)
                .protocol("HTTP")
                .autoRetryErrorCode("404")
                .encodingIso88591(3)
                .encodingGsm7(1)
                .encodingUcs2(2)
                .splitMessage(false)
                .splitSmppType("TLV")
                .build();
    }

    private static MessageEvent getMessageEvent() {
        return MessageEvent.builder()
                .messageId("1719421854353-11028072268459")
                .registeredDelivery(1)
                .originNetworkId(6)
                .systemId("httpgw")
                .deliverSmId("1")
                .sourceAddrNpi(1)
                .sourceAddrTon(1)
                .sourceAddr("50510201020")
                .originProtocol("HTTP")
                .routingId(3)
                .retryDestNetworkId("")
                .destAddrTon(1)
                .destAddrNpi(1)
                .destinationAddr("50582368999")
                .validityPeriod(160)
                .build();
    }

    private static List<RoutingRule> getRoutingRuleList(String protocol) {
        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setPriority(1);
        destination.setNetworkId(6);
        destination.setProtocol(protocol);
        destination.setNetworkType("GW");
        List<RoutingRule.Destination> destinations = List.of(destination);
        return List.of(
                RoutingRule.builder()
                        .id(3)
                        .originNetworkId(6)
                        .sriResponse(false)
                        .destination(destinations)
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
                        .originProtocol(protocol)
                        .originNetworkType("SP")
                        .hasFilterRules(false)
                        .hasActionRules(false)
                        .build()
        );
    }

    private static List<ErrorCodeMapping> getErrorCodeMappingList(Integer errorCode) {
        return List.of(ErrorCodeMapping.builder().errorCode(errorCode).deliveryErrorCode(55).deliveryStatus("DELIVRD").build());
    }

    static void toSleep(long seconds) {
        try (ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor()) {
            executorService.schedule(() -> {
            }, seconds, TimeUnit.SECONDS);
        }
    }
}