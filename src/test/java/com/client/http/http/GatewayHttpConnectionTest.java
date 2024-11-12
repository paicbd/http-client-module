package com.client.http.http;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;
import com.client.http.utils.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.UtilsEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import redis.clients.jedis.JedisCluster;

import ch.qos.logback.classic.spi.ILoggingEvent;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GatewayHttpConnectionTest {

    private ListAppender<ILoggingEvent> listAppender;

    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap = new ConcurrentHashMap<>();

    private final ConcurrentMap<Integer, List<RoutingRule>> routingHashMap = new ConcurrentHashMap<>();

    @Mock
    private HttpClient httpClient;

    @Mock
    private ExecutorService service;

    @InjectMocks
    private GatewayHttpConnection gatewayHttpConnection;

    @Mock
    private AppProperties appProperties;

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private Gateway gateway;

    @Mock
    private CdrProcessor cdrProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        assertNotNull(gateway, "Gateway mock is not initialized");
        assertNotNull(appProperties, "AppProperties mock is not initialized");

        String gatewayJson = "{\"name\":\"gwHttp2\",\"password\":\"\",\"ip\":\"thisisaverylongdomainname123.com\",\"port\":0,\"tps\":1,\"network_id\":3,\"system_id\":\"gwHttp2\",\"bind_type\":\"TRANSCEIVER\",\"system_type\":\"\",\"interface_version\":\"IF_50\",\"sessions_number\":1,\"address_ton\":0,\"address_npi\":0,\"address_range\":\"\",\"enabled\":0,\"enquire_link_period\":30000,\"request_dlr\":false,\"no_retry_error_code\":\"\",\"retry_alternate_destination_error_code\":\"\",\"bind_timeout\":5000,\"bind_retry_period\":10000,\"pdu_timeout\":5000,\"pdu_degree\":1,\"thread_pool_size\":100,\"mno_id\":1,\"status\":\"STOPPED\",\"active_sessions_numbers\":0,\"enquire_link_timeout\":0,\"tlv_message_receipt_id\":false,\"message_id_decimal_format\":false,\"protocol\":\"HTTP\",\"auto_retry_error_code\":\"\",\"encoding_iso88591\":1,\"encoding_gsm7\":1,\"encoding_ucs2\":1,\"split_message\":false,\"split_smpp_type\":\"TLV\"}";

        when(appProperties.getKeyGatewayRedis()).thenReturn("gateways");
        when(jedisCluster.hget(anyString(), anyString())).thenReturn(gatewayJson);

        Gateway gatewayTest = new Gateway();
        gatewayTest.setName("gwHttp2");
        gatewayTest.setNoRetryErrorCode("400, 500");
        gatewayTest.setRetryAlternateDestinationErrorCode("502");
        gatewayTest.setSystemId("sysId001");
        gatewayTest.setIp("http://18.224.164.85:3000/api/callback");
        gatewayTest.setPduTimeout(5000);
        gatewayTest.setAutoRetryErrorCode("404");
        gatewayTest.setMno(1);
        gatewayHttpConnection = new GatewayHttpConnection(
                appProperties,
                jedisCluster,
                gatewayTest,
                errorCodeMappingConcurrentHashMap,
                routingHashMap,
                cdrProcessor
        );

        when(appProperties.getHttpWorkersPerGw()).thenReturn(2);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(10);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(1000);
        when(jedisCluster.llen(anyString())).thenReturn(10L);

        gatewayHttpConnection.getRoutingHashMap().put(1, List.of(new RoutingRule()));
        ErrorCodeMapping errorCodeMapping = new ErrorCodeMapping();
        errorCodeMapping.setErrorCode(502);
        errorCodeMapping.setDeliveryErrorCode(502);
        errorCodeMapping.setDeliveryStatus("DELIVRD");

        List<ErrorCodeMapping> errorCodeMappings = List.of(errorCodeMapping);

        gatewayHttpConnection.getErrorCodeMappingConcurrentHashMap().put("1", errorCodeMappings);

        Logger logger = (Logger) LoggerFactory.getLogger(GatewayHttpConnection.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void testConnect() {
        gatewayHttpConnection.connect();

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Connected to Gateway")));

        assertEquals("STARTED", gatewayHttpConnection.getGateway().getStatus());
    }

    @Test
    void fetchAllItems_ShouldReturnFluxOfLists_WhenBatchSizeIsGreaterThanZero() {
        List<String> mockBatch = List.of("message1", "message2");
        when(jedisCluster.lpop(anyString(), anyInt())).thenReturn(mockBatch);

        Flux<List<String>> result = gatewayHttpConnection.fetchAllItems();

        assertNotNull(result);
        List<List<String>> resultList = result.collectList().block();
        assert resultList != null;
        assertEquals(2, resultList.size());
        assertEquals(mockBatch, resultList.getFirst());

        verify(jedisCluster, times(2)).lpop(anyString(), anyInt());
    }

    @Test
    void fetchAllItems_ShouldReturnEmptyFlux_WhenBatchSizeIsZeroOrNegative() {

        when(appProperties.getHttpWorkersPerGw()).thenReturn(0);
        when(appProperties.getHttpRecordsPerGw()).thenReturn(0);
        when(appProperties.getHttpJobExecuteEvery()).thenReturn(0);
        when(jedisCluster.llen(anyString())).thenReturn(0L);
        Flux<List<String>> result = gatewayHttpConnection.fetchAllItems();

        assertNotNull(result);
        List<List<String>> resultList = result.collectList().block();
        assertNotNull(resultList);
        assertTrue(resultList.isEmpty());

        verify(jedisCluster, never()).lpop(anyString(), anyInt());
    }

    @Test
    void testCastSubmitSmEvent() {
        String validJson = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"message_id\":\"msgId001\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"10\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        MessageEvent expectedMessageEvent = new MessageEvent();
        expectedMessageEvent.setId("12345");
        expectedMessageEvent.setMessageId("msgId001");
        expectedMessageEvent.setSystemId("sysId001");
        expectedMessageEvent.setDeliverSmId("delSmId001");
        expectedMessageEvent.setDeliverSmServerId(null);
        expectedMessageEvent.setCommandStatus(0);
        expectedMessageEvent.setSequenceNumber(1);
        expectedMessageEvent.setSourceAddrTon(1);
        expectedMessageEvent.setSourceAddrNpi(1);
        expectedMessageEvent.setSourceAddr("sourceAddr");
        expectedMessageEvent.setDestAddrTon(1);
        expectedMessageEvent.setDestAddrNpi(1);
        expectedMessageEvent.setDestinationAddr("destAddr");
        expectedMessageEvent.setEsmClass(1);
        expectedMessageEvent.setValidityPeriod(10);
        expectedMessageEvent.setRegisteredDelivery(1);
        expectedMessageEvent.setDataCoding(0);
        expectedMessageEvent.setSmDefaultMsgId(1);
        expectedMessageEvent.setShortMessage("shortMsg");
        expectedMessageEvent.setDelReceipt("receipt");
        expectedMessageEvent.setStatus("status");
        expectedMessageEvent.setErrorCode("errorCode");
        expectedMessageEvent.setCheckSubmitSmResponse(true);
        expectedMessageEvent.setOptionalParameters(List.of());
        expectedMessageEvent.setOriginNetworkType("type");
        expectedMessageEvent.setOriginProtocol("protocol");
        expectedMessageEvent.setOriginNetworkId(1);
        expectedMessageEvent.setDestNetworkType("type");
        expectedMessageEvent.setDestProtocol("protocol");
        expectedMessageEvent.setDestNetworkId(2);
        expectedMessageEvent.setRoutingId(1);
        expectedMessageEvent.setMsisdn("msisdn");
        expectedMessageEvent.setAddressNatureMsisdn(1);
        expectedMessageEvent.setNumberingPlanMsisdn(1);
        expectedMessageEvent.setRemoteDialogId(123456789L);
        expectedMessageEvent.setLocalDialogId(987654321L);
        expectedMessageEvent.setSccpCalledPartyAddressPointCode(1);
        expectedMessageEvent.setSccpCalledPartyAddressSubSystemNumber(2);
        expectedMessageEvent.setSccpCalledPartyAddress("address");
        expectedMessageEvent.setSccpCallingPartyAddressPointCode(3);
        expectedMessageEvent.setSccpCallingPartyAddressSubSystemNumber(4);
        expectedMessageEvent.setSccpCallingPartyAddress("address");
        expectedMessageEvent.setGlobalTitle("title");
        expectedMessageEvent.setGlobalTitleIndicator("indicator");
        expectedMessageEvent.setTranslationType(1);
        expectedMessageEvent.setSmscSsn(1);
        expectedMessageEvent.setHlrSsn(2);
        expectedMessageEvent.setMscSsn(3);
        expectedMessageEvent.setMapVersion(1);
        expectedMessageEvent.setRetry(false);
        expectedMessageEvent.setRetryDestNetworkId("networkId");
        expectedMessageEvent.setRetryNumber(1);
        expectedMessageEvent.setLastRetry(false);
        expectedMessageEvent.setNetworkNotifyError(false);
        expectedMessageEvent.setDueDelay(10);
        expectedMessageEvent.setAccumulatedTime(20);
        expectedMessageEvent.setDropMapSri(false);
        expectedMessageEvent.setNetworkIdToMapSri(1);
        expectedMessageEvent.setNetworkIdToPermanentFailure(2);
        expectedMessageEvent.setDropTempFailure(false);
        expectedMessageEvent.setNetworkIdTempFailure(3);
        expectedMessageEvent.setImsi("imsi");
        expectedMessageEvent.setNetworkNodeNumber("nodeNumber");
        expectedMessageEvent.setNetworkNodeNumberNatureOfAddress(1);
        expectedMessageEvent.setNetworkNodeNumberNumberingPlan(1);
        expectedMessageEvent.setMoMessage(false);
        expectedMessageEvent.setSriResponse(false);
        expectedMessageEvent.setCheckSriResponse(false);
        expectedMessageEvent.setMsgReferenceNumber("refNum");
        expectedMessageEvent.setTotalSegment(1);
        expectedMessageEvent.setSegmentSequence(1);
        expectedMessageEvent.setOriginatorSccpAddress("address");
        expectedMessageEvent.setUdhi("udhi");
        expectedMessageEvent.setUdhJson("udhJson");
        expectedMessageEvent.setParentId("parentId");
        expectedMessageEvent.setDlr(false);
        expectedMessageEvent.setMessageParts(List.of());
        expectedMessageEvent.setProcess(true);

        MessageEvent result = Converter.stringToObject(validJson, MessageEvent.class);

        assertEquals(expectedMessageEvent.toString(), result.toString(), "The objects MessageEvent are not equal.");

    }

    @Test
    void testInvalidJson() {
        String invalidJson = "{ \"invalidField";
        MessageEvent result = Converter.stringToObject(invalidJson, MessageEvent.class);
        assertNull(result);
    }

    @Test
    @Order(1)
    void addInCache_ShouldAddSubmitSmToCache() throws Exception {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"message_id\":\"msgId001\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"10\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(submitInRaw);

        MessageEvent submitSmEvent = mock(MessageEvent.class);
        when(submitSmEvent.getRegisteredDelivery()).thenReturn(1);
        when(submitSmEvent.getId()).thenReturn("submitSmId001");
        when(submitSmEvent.getSystemId()).thenReturn("sysId001");
        when(submitSmEvent.getMessageId()).thenReturn("msgId001");
        when(submitSmEvent.getOriginProtocol()).thenReturn("protocol");
        when(submitSmEvent.getOriginNetworkId()).thenReturn(1);
        when(submitSmEvent.getParentId()).thenReturn("parentId");

        invokePrivateMethod(gatewayHttpConnection, "addInCache", new Class<?>[]{MessageEvent.class, HttpResponse.class}, submitSmEvent, response);

        verify(jedisCluster).hset(
                eq(appProperties.getSubmitSmResultQueue()),
                eq("MSGID001"),
                anyString()
        );
    }

    @Test
    void addInCache_ShouldLogWarningWhenNoMessageId() throws Exception {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"10\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(submitInRaw);

        MessageEvent submitSmEvent = mock(MessageEvent.class);
        when(submitSmEvent.getRegisteredDelivery()).thenReturn(1);
        when(submitSmEvent.getId()).thenReturn("submitSmId001");
        when(submitSmEvent.getSystemId()).thenReturn("sysId001");
        when(submitSmEvent.getMessageId()).thenReturn(null); // No message_id
        when(submitSmEvent.getOriginProtocol()).thenReturn("protocol");
        when(submitSmEvent.getOriginNetworkId()).thenReturn(1);
        when(submitSmEvent.getParentId()).thenReturn("parentId");

        invokePrivateMethod(gatewayHttpConnection, "addInCache", new Class<?>[]{MessageEvent.class, HttpResponse.class}, submitSmEvent, response);

        List<ILoggingEvent> logsList = listAppender.list;
        logsList.forEach(log -> System.out.println(log.getFormattedMessage()));

        assertTrue(logsList.stream()
                .anyMatch(log -> log.getLevel().toString().equals("WARN") &&
                                 log.getMessage().contains("The response body doesn't contains message_id")));
    }

    private void invokePrivateMethod(Object targetObject, String methodName, Class<?>[] parameterTypes, Object... parameters)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = targetObject.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(targetObject, parameters);
    }

    @Test
    void testPrepareForRetry_EntersIf() throws Exception {
        MessageEvent submitSmEventToRetry = Mockito.mock(MessageEvent.class);
        when(submitSmEventToRetry.getRetryDestNetworkId()).thenReturn("");
        when(submitSmEventToRetry.getDestNetworkId()).thenReturn(123);

        Map.Entry<Integer, String> alternativeRoute = Map.entry(456, "alternativeProtocol");

        invokePrivateMethod(gatewayHttpConnection, "prepareForRetry", new Class<?>[]{MessageEvent.class, Map.Entry.class}, submitSmEventToRetry, alternativeRoute);

        Mockito.verify(submitSmEventToRetry).setRetry(true);
        Mockito.verify(submitSmEventToRetry).setRetryDestNetworkId("123");
        Mockito.verify(submitSmEventToRetry).setDestNetworkId(456);
        Mockito.verify(submitSmEventToRetry).setDestProtocol("alternativeProtocol");
    }

    @Test
    void testSendToRetryProcess() {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"10\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        MessageEvent messageEvent = Converter.stringToObject(submitInRaw, MessageEvent.class);

        gatewayHttpConnection.sendToRetryProcess(messageEvent, 500);

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Starting retry process for submit_sm with id")));
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Failed to retry for submit_sm with id")));
    }

    @Test
    void testSendToRetryProcess_handlerRetryAlternateDestination_NoAlternative() {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"120\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        MessageEvent messageEvent = Converter.stringToObject(submitInRaw, MessageEvent.class);

        gatewayHttpConnection.sendToRetryProcess(messageEvent, 502);
        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Starting retry process for submit_sm with id")));
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Failed to retry for submit_sm with id")));
    }

    @Test
    void testSendToRetryProcess_handleAutoRetry() {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"120\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";

        MessageEvent messageEvent = Converter.stringToObject(submitInRaw, MessageEvent.class);

        gatewayHttpConnection.sendToRetryProcess(messageEvent, 402);
        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Failed to retry for submit_sm with id")));
    }

    @Test
    void testSendToRetryProcess_handlerRetryAlternateDestination_OptionalRouting() {
        executeSendToRetryProcessTest("HTTP");
    }

    @Test
    void testSendToRetryProcess_handlerRetryAlternateDestination_OptionalRouting_SMPP() {
        executeSendToRetryProcessTest("SMPP");
    }

    @Test
    void testSendToRetryProcess_handlerRetryAlternateDestination_OptionalRouting_SS7() {
        executeSendToRetryProcessTest("SS7");
    }

    private void executeSendToRetryProcessTest(String protocol) {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"120\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"protocol\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";
        String routingRulesJson = "[{\"id\":1,\"origin_network_id\":1,\"origin_protocol\":\"HTTP\",\"network_id_to_map_sri\":0,\"network_id_to_permanent_failure\":0,\"network_id_temp_failure\":0,\"drop_temp_failure\":false,\"has_filter_rules\":false,\"has_action_rules\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"drop_map_sri\":false,\"new_source_addr\":\"\",\"new_source_addr_ton\":0,\"new_source_addr_npi\":0,\"new_destination_addr\":\"\",\"new_dest_addr_ton\":0,\"new_dest_addr_npi\":0,\"add_source_addr_prefix\":\"\",\"remove_source_addr_prefix\":\"\",\"add_dest_addr_prefix\":\"\",\"remove_dest_addr_prefix\":\"\",\"new_gt_sccp_addr_mt\":\"\",\"origin_network_type\":\"\",\"regex_source_addr\":\"\",\"regex_source_addr_ton\":\"\",\"regex_source_addr_npi\":\"\",\"regex_destination_addr\":\"\",\"regex_dest_addr_ton\":\"\",\"regex_dest_addr_npi\":\"\",\"regex_imsi_digits_mask\":\"\",\"regex_network_node_number\":\"\",\"regex_calling_party_address\":\"\",\"destination\":[]}]";

        MessageEvent messageEvent = Converter.stringToObject(submitInRaw, MessageEvent.class);

        RoutingRule.Destination destination = new RoutingRule.Destination();
        destination.setNetworkId(3);
        destination.setPriority(1);
        destination.setProtocol(protocol);
        destination.setNetworkType("GW");

        gatewayHttpConnection.getRoutingHashMap().put(1, List.of(Converter.stringToObject(routingRulesJson, new TypeReference<>() {
        })));

        gatewayHttpConnection.getRoutingHashMap().get(1).getFirst().setDestination(List.of(destination));

        gatewayHttpConnection.sendToRetryProcess(messageEvent, 502);
        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Starting retry process for submit_sm with id")));
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Retry for submit_sm with id")));
    }

    @ParameterizedTest
    @MethodSource("provideProtocolsAndExpectedMessages")
    void testHandlingErrorOnSendMessage_origin(String originProtocol, String expectedMessage) {
        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"120\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":[]}";
        MessageEvent messageEvent = Converter.stringToObject(submitInRaw, MessageEvent.class);
        int errorCode = 502;
        messageEvent.setOriginProtocol(originProtocol);

        assertDoesNotThrow(() ->
                gatewayHttpConnection.globalErrorHandler(messageEvent, errorCode)
        );

        messageEvent.setValidityPeriod(0);
        assertDoesNotThrow(() ->
                gatewayHttpConnection.globalErrorHandler(messageEvent, errorCode)
        );

        messageEvent.setValidityPeriod(120);
        messageEvent.setLastRetry(true);
        assertDoesNotThrow(() ->
                gatewayHttpConnection.globalErrorHandler(messageEvent, errorCode)
        );
    }

    private static Stream<Arguments> provideProtocolsAndExpectedMessages() {
        return Stream.of(
                Arguments.of("HTTP", "Last retry for submit_sm with id"),
                Arguments.of("HTTP", "Creating deliver_sm with status"),
                Arguments.of("SMPP", "Last retry for submit_sm with id"),
                Arguments.of("INVALID", "Invalid Origin Protocol")
        );
    }

    @Test
    void testErrorContainedWithValidErrorCode() {
        String stringList = "404, 500, 502";
        int errorCode = 500;

        boolean result = gatewayHttpConnection.errorContained(stringList, errorCode);

        assertTrue(result, "The error code should be contained in the list.");
    }

    @Test
    void testHandleSubmitSm_messagePartsNull() throws InvocationTargetException, NoSuchMethodException, IllegalAccessException, IOException, InterruptedException {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(Integer.parseInt("200"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString()))).thenReturn(mockResponse);

        String submitInRaw = "{\"msisdn\":\"msisdn\",\"id\":\"12345\",\"system_id\":\"sysId001\",\"deliver_sm_id\":\"delSmId001\",\"deliver_sm_server_id\":null,\"command_status\":0,\"sequence_number\":1,\"source_addr_ton\":1,\"source_addr_npi\":1,\"source_addr\":\"sourceAddr\",\"dest_addr_ton\":1,\"dest_addr_npi\":1,\"destination_addr\":\"destAddr\",\"esm_class\":1,\"validity_period\":\"120\",\"registered_delivery\":1,\"data_coding\":0,\"sm_default_msg_id\":1,\"short_message\":\"shortMsg\",\"delivery_receipt\":\"receipt\",\"status\":\"status\",\"error_code\":\"errorCode\",\"check_submit_sm_response\":true,\"optional_parameters\":[],\"origin_network_type\":\"type\",\"origin_protocol\":\"HTTP\",\"origin_network_id\":1,\"dest_network_type\":\"type\",\"dest_protocol\":\"protocol\",\"dest_network_id\":2,\"routing_id\":1,\"address_nature_msisdn\":1,\"numbering_plan_msisdn\":1,\"remote_dialog_id\":123456789,\"local_dialog_id\":987654321,\"sccp_called_party_address_pc\":1,\"sccp_called_party_address_ssn\":2,\"sccp_called_party_address\":\"address\",\"sccp_calling_party_address_pc\":3,\"sccp_calling_party_address_ssn\":4,\"sccp_calling_party_address\":\"address\",\"global_title\":\"title\",\"global_title_indicator\":\"indicator\",\"translation_type\":1,\"smsc_ssn\":1,\"hlr_ssn\":2,\"msc_ssn\":3,\"map_version\":1,\"is_retry\":false,\"retry_dest_network_id\":\"networkId\",\"retry_number\":1,\"is_last_retry\":false,\"is_network_notify_error\":false,\"due_delay\":10,\"accumulated_time\":20,\"drop_map_sri\":false,\"network_id_to_map_sri\":1,\"network_id_to_permanent_failure\":2,\"drop_temp_failure\":false,\"network_id_temp_failure\":3,\"imsi\":\"imsi\",\"network_node_number\":\"nodeNumber\",\"network_node_number_nature_of_address\":1,\"network_node_number_numbering_plan\":1,\"mo_message\":false,\"is_sri_response\":false,\"check_sri_response\":false,\"msg_reference_number\":\"refNum\",\"total_segment\":1,\"segment_sequence\":1,\"originator_sccp_address\":\"address\",\"udhi\":\"udhi\",\"udh_json\":\"udhJson\",\"parent_id\":\"parentId\",\"is_dlr\":false,\"message_parts\":null}";

        when(service.submit(any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        });

        invokePrivateMethod(gatewayHttpConnection, "handleSubmitSm", new Class<?>[]{String.class}, submitInRaw);

        verify(service, never()).execute(any(Runnable.class));
    }

    @Test
    void handlerCdrDetail_createsCdr() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MessageEvent messageEvent = new MessageEvent();
        UtilsEnum.MessageType messageType = UtilsEnum.MessageType.DELIVER;
        UtilsEnum.CdrStatus cdrStatus = UtilsEnum.CdrStatus.SENT;
        boolean createCdr = true;
        String message = "Test Message";

        invokePrivateMethod(gatewayHttpConnection, "handlerCdrDetail",
                new Class<?>[]{MessageEvent.class, UtilsEnum.MessageType.class, UtilsEnum.CdrStatus.class, CdrProcessor.class, boolean.class, String.class},
                messageEvent, messageType, cdrStatus, cdrProcessor, createCdr, message);

        verify(cdrProcessor).putCdrDetailOnRedis(any());
        verify(cdrProcessor).createCdr(messageEvent.getMessageId());
    }

    @Test
    void handlerCdrDetail_doesNotCreateCdr() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MessageEvent messageEvent = new MessageEvent();
        UtilsEnum.MessageType messageType = UtilsEnum.MessageType.DELIVER;
        UtilsEnum.CdrStatus cdrStatus = UtilsEnum.CdrStatus.FAILED;
        boolean createCdr = false;
        String message = "Test Message";

        invokePrivateMethod(gatewayHttpConnection, "handlerCdrDetail",
                new Class<?>[]{MessageEvent.class, UtilsEnum.MessageType.class, UtilsEnum.CdrStatus.class, CdrProcessor.class, boolean.class, String.class},
                messageEvent, messageType, cdrStatus, cdrProcessor, createCdr, message);

        verify(cdrProcessor).putCdrDetailOnRedis(any());
        verify(cdrProcessor, never()).createCdr(any());
    }

    @Test
    void testDetermineListName_CaseDefault() throws Exception {
        invokePrivateMethod(
                gatewayHttpConnection,
                "determineListName",
                new Class<?>[]{String.class, Integer.class},
                "unknownProtocol",
                123
        );

        List<ILoggingEvent> logsList = listAppender.list;
        assertTrue(logsList.stream().anyMatch(log -> log.getFormattedMessage().contains("Invalid Destination Protocol")));
    }
}