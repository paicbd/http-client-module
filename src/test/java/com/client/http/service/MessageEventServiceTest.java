package com.client.http.service;

import com.client.http.dto.GlobalRecords;
import com.client.http.utils.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisCluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageEventServiceTest {

    @Mock
    private JedisCluster jedisCluster;

    @Mock
    private AppProperties appProperties;

    @InjectMocks
    private MessageEventService messageEventService;


    @Test
    @DisplayName("Don't put the DLR request in the HTTP request queue.")
    void processDeliveryWhenDLRRequestNotFoundThenDoNothing() {
        String messageId = "1719421854353-11028072268459";
        GlobalRecords.DlrRequest dlrRequest = generateDlrRequest(messageId);
        when(appProperties.getSubmitSmResultQueue()).thenReturn("http_submit_sm_result");
        when(jedisCluster.hget(appProperties.getSubmitSmResultQueue(), messageId.toUpperCase())).thenReturn(null);

        GlobalRecords.MessageResponse response = messageEventService.processDelivery(dlrRequest);

        verifyNoMoreInteractions(jedisCluster);
        assertNotNull(response);
        assertEquals("No DLR sent, messageId not found", response.errorMessage());
    }

    @Test
    @DisplayName("Putting the DLR request in the HTTP request queue.")
    void processDeliveryWhenDLRRequestFoundThenCheckValues() {
        String messageId = "1719421854353-11028072268459";
        GlobalRecords.DlrRequest dlrRequest = generateDlrRequest(messageId);

        when(appProperties.getSubmitSmResultQueue()).thenReturn("http_submit_sm_result");
        when(jedisCluster.hget(appProperties.getSubmitSmResultQueue(), messageId.toUpperCase())).thenReturn(dlrRequest.toString());

        GlobalRecords.MessageResponse response = messageEventService.processDelivery(dlrRequest);

        verify(jedisCluster).rpush("http_dlr_request", dlrRequest.toString());
        assertNotNull(response);
        assertEquals("DLR sent successfully", response.errorMessage());
    }

    private GlobalRecords.DlrRequest generateDlrRequest(String messageId) {
        return new GlobalRecords.DlrRequest(
                messageId,
                1,
                4,
                "50510201020",
                1,
                4,
                "50582368999",
                0,
                "UNDELIV",
                "500",
                null
        );
    }
}