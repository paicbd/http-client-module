package com.client.http.controller;

import com.client.http.dto.GlobalRecords;
import com.client.http.service.MessageEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.springframework.http.HttpStatus.OK;

@ExtendWith(MockitoExtension.class)
class SmsControllerTest {

    @Mock
    private MessageEventService messageEventService;

    @InjectMocks
    private SmsController smsController;

    @Test
    @DisplayName("Starting the request watcher")
    void smsControllerWhenStartWatcherThenExecutedPostConstruct() {
        SmsController spySmsController = spy(smsController);
        spySmsController.init();
        verify(spySmsController).init();
    }

    @Test
    @DisplayName("Sending DLR and verifying that the response is OK")
    void deliveryWhenSendDLRThenResponseIsOK() {
        GlobalRecords.DlrRequest dlrRequest = new GlobalRecords.DlrRequest(
                "1719421854353-11028072268459", 1, 4, "50510201020",
                1, 4, "50582368999", 1,
                "UNDELIV", "500", null
        );
        Mono<ResponseEntity<GlobalRecords.MessageResponse>> response = smsController.delivery(dlrRequest);
        ResponseEntity<GlobalRecords.MessageResponse> result = response.block();
        assertNotNull(result);
        assertEquals(OK, result.getStatusCode());
        verify(messageEventService).processDelivery(dlrRequest);
    }
}
