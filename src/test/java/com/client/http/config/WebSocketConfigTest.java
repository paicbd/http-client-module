package com.client.http.config;

import com.client.http.utils.AppProperties;
import com.paicbd.smsc.ws.SocketClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketConfigTest {

    @Mock
    private AppProperties mockAppProperties;

    @InjectMocks
    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() {
        when(mockAppProperties.isWebsocketEnabled()).thenReturn(true);
        when(mockAppProperties.getWebSocketHost()).thenReturn("localhost");
        when(mockAppProperties.getWebSocketPort()).thenReturn(9000);
        when(mockAppProperties.getWebSocketPath()).thenReturn("/ws");
        when(mockAppProperties.getWebsocketHeaderName()).thenReturn("Authorization");
        when(mockAppProperties.getWebsocketHeaderValue()).thenReturn("fcb13146-ecd7-46a5-b9cb-a1e75fae9bdc");
        when(mockAppProperties.getWebsocketRetryInterval()).thenReturn(10);
    }

    @Test
    void socketClient() {
        SocketClient socketClient = webSocketConfig.socketClient();
        assertNotNull(socketClient, "SocketClient should not be null");
    }

    @Test
    void socketClient_connectionFailure() {
        when(mockAppProperties.getWebSocketPort()).thenThrow(new RuntimeException("Connection failed"));
        assertThrows(RuntimeException.class, () -> {
            webSocketConfig.socketClient();
        }, "Expected RuntimeException to be thrown");
    }

    @Test
    void socketClient_retryConnection() throws InterruptedException {
        when(mockAppProperties.isWebsocketEnabled()).thenReturn(true);
        when(mockAppProperties.getWebsocketRetryInterval()).thenReturn(10);
        when(mockAppProperties.getWebSocketHost()).thenReturn("localhost");

        when(mockAppProperties.getWebSocketPort())
                .thenThrow(new RuntimeException("Connection failed"))
                .thenReturn(9000);

        Thread thread = new Thread(() -> {
            SocketClient socketClient = webSocketConfig.socketClient();
            assertNotNull(socketClient, "SocketClient should not be null after retry");
        });
        thread.start();
        thread.join(20);
    }
}