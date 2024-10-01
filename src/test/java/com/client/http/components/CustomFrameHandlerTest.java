package com.client.http.components;

import com.client.http.http.HttpClientManager;
import com.paicbd.smsc.ws.SocketSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;

import static com.client.http.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.CONNECT_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.DELETE_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_ROUTING_RULES_ENDPOINT;
import static com.client.http.utils.Constants.DELETE_ROUTING_RULES_ENDPOINT;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CustomFrameHandlerTest {

    private static final String PAYLOAD_SYSTEM_ID = "systemId123";

    @Mock
    private SocketSession mockSocketSession;

    @Mock
    private HttpClientManager mockHttpClientManager;

    @Mock
    private StompHeaders mockStompHeaders;

    @Mock
    private StompSession mockStompSession;

    @InjectMocks
    private CustomFrameHandler customFrameHandler;

    @BeforeEach
    public void setUp() {
        when(mockSocketSession.getStompSession()).thenReturn(mockStompSession);
    }

    @Test
    void handleFrameLogic_updateGateway() {
        when(mockStompHeaders.getDestination()).thenReturn(UPDATE_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).updateGateway(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_connectGateway() {
        when(mockStompHeaders.getDestination()).thenReturn(CONNECT_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).connectGateway(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_stopGateway() {
        when(mockStompHeaders.getDestination()).thenReturn(STOP_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).stopGateway(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_deleteGateway() {
        when(mockStompHeaders.getDestination()).thenReturn(DELETE_GATEWAY_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).deleteGateway(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_updateErrorCodeMapping() {
        when(mockStompHeaders.getDestination()).thenReturn(UPDATE_ERROR_CODE_MAPPING_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).updateErrorCodeMapping(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_updateServiceProvider() {
        when(mockStompHeaders.getDestination()).thenReturn(UPDATE_SERVICE_PROVIDER_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).updateServiceProvider(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_updateRoutingRules() {
        when(mockStompHeaders.getDestination()).thenReturn(UPDATE_ROUTING_RULES_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).updateRoutingRules(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_deleteRoutingRules() {
        when(mockStompHeaders.getDestination()).thenReturn(DELETE_ROUTING_RULES_ENDPOINT);
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verify(mockHttpClientManager).deleteRoutingRules(PAYLOAD_SYSTEM_ID);
    }

    @Test
    void handleFrameLogic_unknownDestination() {
        when(mockStompHeaders.getDestination()).thenReturn("unknown_destination");
        assertDoesNotThrow(() -> customFrameHandler.handleFrameLogic(mockStompHeaders, PAYLOAD_SYSTEM_ID));
        verifyNoInteractions(mockHttpClientManager);
    }
}
