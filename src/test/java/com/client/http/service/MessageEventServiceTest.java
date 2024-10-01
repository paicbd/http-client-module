package com.client.http.service;

import com.client.http.dto.GlobalRecords;
import com.client.http.http.GatewayHttpConnection;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import redis.clients.jedis.JedisCluster;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.client.http.utils.Constants.IS_STARTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MessageEventServiceTest {

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @Mock
    private ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList;

    @Mock
    private CdrProcessor cdrProcessor;

    @InjectMocks
    private MessageEventService messageEventService;

    private GatewayHttpConnection gatewayHttpConnection;

    @BeforeEach
    void setUp() {
        Gateway gatewayTest = new Gateway();
        gatewayTest.setName("gwHttp2");
        gatewayTest.setNetworkId(1);
        gatewayTest.setProtocol("HTTP");
        gatewayTest.setEnabled(IS_STARTED);

        gatewayHttpConnection = new GatewayHttpConnection(
                appProperties,
                jedisCluster,
                gatewayTest,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                cdrProcessor
        );

        when(httpConnectionManagerList.getOrDefault(anyString(), any())).thenReturn(gatewayHttpConnection);

        when(appProperties.getPreDeliverQueue()).thenReturn("someQueue");

        messageEventService = new MessageEventService(jedisCluster, appProperties, httpConnectionManagerList);
    }

    @Test
    void testProcessDelivery_messageIdNotFound() {
        String messageId = "testMessageId";
        GlobalRecords.DlrRequest dlrRequest = generateDrRequest(messageId);

        when(jedisCluster.hget(anyString(), anyString())).thenReturn(null);

        GlobalRecords.MessageResponse response = messageEventService.processDelivery(dlrRequest);

        assertNotNull(response);
        assertEquals("No DLR sent, messageId not found", response.errorMessage());
    }

    @Test
    void testProcessDelivery_success() {
        String messageId = "testMessageId";
        GlobalRecords.DlrRequest dlrRequest = generateDrRequest(messageId);

        when(appProperties.getSubmitSmResultQueue()).thenReturn("submitSmResultQueue");
        when(jedisCluster.hget("submitSmResultQueue", messageId)).thenReturn("someResponse");
        when(jedisCluster.rpush("http_dlr_request", dlrRequest.toString())).thenReturn(1L);

        GlobalRecords.MessageResponse response = messageEventService.processDelivery(dlrRequest);

        assertNotNull(response);
    }

    @Test
    void testValidateAndProcessMessage_gatewayNotFound() {
        MessageEvent pdu = new MessageEvent();
        pdu.setSystemId("someSystemId");

        Gateway mockGateway = Mockito.mock(Gateway.class);
        when(mockGateway.getEnabled()).thenReturn(0);

        ReflectionTestUtils.setField(gatewayHttpConnection, "gateway", mockGateway);

        GatewayHttpConnection gatewayHttpConnectionSpy = Mockito.spy(gatewayHttpConnection);

        when(httpConnectionManagerList.getOrDefault(anyString(), any())).thenReturn(gatewayHttpConnectionSpy);

        GlobalRecords.MessageResponse response = messageEventService.validateAndProcessMessage(pdu);

        assertNotNull(response);
        assertEquals("Service not found", response.errorMessage());
    }

    @Test
    void testValidateAndProcessMessage_success() {
        MessageEvent pdu = new MessageEvent();
        pdu.setSystemId("someSystemId");
        pdu.setDataCoding(0);

        Gateway mockGateway = Mockito.mock(Gateway.class);
        when(mockGateway.getEnabled()).thenReturn(IS_STARTED);

        ReflectionTestUtils.setField(gatewayHttpConnection, "gateway", mockGateway);

        GatewayHttpConnection gatewayHttpConnectionSpy = Mockito.spy(gatewayHttpConnection);

        when(httpConnectionManagerList.getOrDefault(anyString(), any())).thenReturn(gatewayHttpConnectionSpy);

        messageEventService.validateAndProcessMessage(pdu);

        verify(jedisCluster).lpush(eq("someQueue"), any(String.class));
    }

    @Test
    void testValidateAndProcessMessage_invalidDataCoding() {
        MessageEvent pdu = new MessageEvent();
        pdu.setSystemId("someSystemId");
        pdu.setDataCoding(2);

        Gateway mockGateway = Mockito.mock(Gateway.class);
        when(mockGateway.getEnabled()).thenReturn(IS_STARTED);

        ReflectionTestUtils.setField(gatewayHttpConnection, "gateway", mockGateway);

        GatewayHttpConnection gatewayHttpConnectionSpy = Mockito.spy(gatewayHttpConnection);

        when(httpConnectionManagerList.getOrDefault(anyString(), any())).thenReturn(gatewayHttpConnectionSpy);

        messageEventService.validateAndProcessMessage(pdu);
        GlobalRecords.MessageResponse response = messageEventService.validateAndProcessMessage(pdu);

        assertNotNull(response);
        assertEquals("Invalid data coding", response.errorMessage());
    }

    private GlobalRecords.DlrRequest generateDrRequest(String messageId) {
        return new GlobalRecords.DlrRequest(
                messageId,
                1,
                1,
                "sourceAddr",
                1,
                1,
                "destinationAddr",
                0,
                "DELIVERED",
                null,
                List.of()
        );
    }
}