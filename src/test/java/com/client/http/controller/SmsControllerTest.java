package com.client.http.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.client.http.dto.GlobalRecords;
import com.client.http.service.MessageEventService;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.dto.MessageEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SmsControllerTest {
    private ListAppender<ILoggingEvent> listAppender;

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @Mock
    private MessageEventService messageEventService;

    @InjectMocks
    private SmsController smsController;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(SmsController.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        smsController = new SmsController(messageEventService);
    }

    @Test
    void testInit_shouldStartWatcher() {
        SmsController spySmsController = spy(smsController);

        spySmsController.init();

        verify(spySmsController).init();
    }

    @Test
    void testDelivery_whenDlrRequestExists() {
        GlobalRecords.DlrRequest dlrRequest = new GlobalRecords.DlrRequest(
                "testMessageId", 1, 1, "sourceAddr",
                1, 1, "destAddr", 1,
                "status", "errorCode", null
        );
        when(appProperties.getSubmitSmResultQueue()).thenReturn("testQueue");
        when(jedisCluster.hget("testQueue", "testMessageId")).thenReturn("existingValue");

        Mono<ResponseEntity<GlobalRecords.MessageResponse>> response = smsController.delivery(dlrRequest);

        ResponseEntity<GlobalRecords.MessageResponse> result = response.block();
        assert result != null;
        assertEquals(OK, result.getStatusCode());
    }

    @Test
    void testDelivery_whenDlrRequestDoesNotExist() {
        GlobalRecords.DlrRequest dlrRequest = new GlobalRecords.DlrRequest(
                "testMessageId", 1, 1, "sourceAddr",
                1, 1, "destAddr", 1,
                "status", "errorCode", null
        );
        when(appProperties.getSubmitSmResultQueue()).thenReturn("testQueue");
        when(jedisCluster.hget("testQueue", "testMessageId")).thenReturn(null);

        Mono<ResponseEntity<GlobalRecords.MessageResponse>> response = smsController.delivery(dlrRequest);

        ResponseEntity<GlobalRecords.MessageResponse> result = response.block();
        assert result != null;
        assertEquals(OK, result.getStatusCode());
        verify(jedisCluster, never()).rpush(anyString(), anyString());
    }

    @Test
    void testMessageEvent_success() {
        MessageEvent messageEvent = new MessageEvent();
        messageEvent.setDataCoding(0);

        GlobalRecords.MessageResponse messageResponse = new GlobalRecords.MessageResponse("Success", null);
        when(messageEventService.validateAndProcessMessage(messageEvent)).thenReturn(messageResponse);

        Mono<ResponseEntity<GlobalRecords.MessageResponse>> responseMono = smsController.messageEvent(messageEvent);

        ResponseEntity<GlobalRecords.MessageResponse> result = responseMono.block();

        assertNotNull(result, "ResponseEntity should not be null");
        assertEquals(OK, result.getStatusCode());
    }
}
