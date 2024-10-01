package com.client.http.components;

import com.client.http.http.HttpClientManager;
import com.paicbd.smsc.ws.FrameHandler;
import com.paicbd.smsc.ws.SocketSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.client.http.utils.Constants.UPDATE_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.CONNECT_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.STOP_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.DELETE_GATEWAY_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_ERROR_CODE_MAPPING_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_SERVICE_PROVIDER_ENDPOINT;
import static com.client.http.utils.Constants.UPDATE_ROUTING_RULES_ENDPOINT;
import static com.client.http.utils.Constants.DELETE_ROUTING_RULES_ENDPOINT;
import static com.client.http.utils.Constants.RESPONSE_SMPP_CLIENT_ENDPOINT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomFrameHandler implements FrameHandler  {
    private final SocketSession socketSession;
    private final HttpClientManager httpClientManager;

    @Override
    public void handleFrameLogic(StompHeaders headers, Object payload) {
        String systemId = payload.toString();
        String destination = headers.getDestination();
        Objects.requireNonNull(systemId, "System ID cannot be null");
        Objects.requireNonNull(destination, "Destination cannot be null");

        switch (destination) {
            case UPDATE_GATEWAY_ENDPOINT -> handleUpdate(systemId, this::handleUpdateGateway);
            case CONNECT_GATEWAY_ENDPOINT -> handleUpdate(systemId, this::handleConnectGateway);
            case STOP_GATEWAY_ENDPOINT -> handleUpdate(systemId, this::handleStopGateway);
            case DELETE_GATEWAY_ENDPOINT -> handleUpdate(systemId, this::handleDeleteGateway);
            case UPDATE_ERROR_CODE_MAPPING_ENDPOINT -> handleUpdate(systemId, this::handleUpdateErrorCodeMapping);
            case UPDATE_SERVICE_PROVIDER_ENDPOINT -> handleUpdate(systemId, this::handleUpdateServiceProvider);
            case UPDATE_ROUTING_RULES_ENDPOINT -> handleUpdate(systemId, this::handleUpdateRoutingRules);
            case DELETE_ROUTING_RULES_ENDPOINT -> handleUpdate(systemId, this::handleDeleteRoutingRules);
            default -> log.warn("Unknown destination: {}", destination);
        }
    }

    private void handleUpdate(String payload, java.util.function.Consumer<String> handler) {
        handler.accept(payload);
        socketSession.getStompSession().send(RESPONSE_SMPP_CLIENT_ENDPOINT, "OK");
    }

    private void handleUpdateGateway(String systemId) {
        log.info("Updating gateway {}", systemId);
        httpClientManager.updateGateway(systemId);
    }

    private void handleConnectGateway(String systemId) {
        log.info("Connecting gateway {}", systemId);
        httpClientManager.connectGateway(systemId);
    }

    private void handleStopGateway(String systemId) {
        log.info("Stopping gateway {}", systemId);
        httpClientManager.stopGateway(systemId);
    }

    private void handleDeleteGateway(String systemId) {
        log.info("Deleting gateway {}", systemId);
        httpClientManager.deleteGateway(systemId);
    }

    private void handleUpdateErrorCodeMapping(String systemId) {
        log.info("Updating error code mapping for mno_id {}", systemId);
        httpClientManager.updateErrorCodeMapping(systemId);
    }

    private void handleUpdateServiceProvider(String systemId) {
        log.info("Updating service provider {}", systemId);
        httpClientManager.updateServiceProvider(systemId);
    }

    private void handleUpdateRoutingRules(String systemId) {
        log.info("Updating routing rules {}", systemId);
        httpClientManager.updateRoutingRules(systemId);
    }

    private void handleDeleteRoutingRules(String systemId) {
        log.info("Deleting routing rules {}", systemId);
        httpClientManager.deleteRoutingRules(systemId);
    }
}
