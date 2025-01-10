package com.client.http.http;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.client.http.utils.Constants;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.ws.SocketSession;
import com.fasterxml.jackson.core.type.TypeReference;
import com.client.http.utils.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisCluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component("httpClientManager")
@RequiredArgsConstructor
public class HttpClientManager {
    private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
    private final SocketSession socketSession;

    private final ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList;
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    private final ConcurrentMap<Integer, List<RoutingRule>> routingHashMap;

    private final CdrProcessor cdrProcessor;

    @PostConstruct
    public void startManager() {
        loadHttpConnectionManager();
        loadErrorCodeMapping();
        loadRoutingRules();
    }

    private void loadHttpConnectionManager() {
        try {
            var gatewaysMaps = this.jedisCluster.hgetAll(this.appProperties.getKeyGatewayRedis());
            if (gatewaysMaps.isEmpty()) {
                log.warn("No gateways found on loadHttpConnectionManager");
                return;
            }

            gatewaysMaps.values().forEach(gatewayInRaw -> {
                Gateway gateway = Converter.stringToObject(gatewayInRaw, Gateway.class);
                Objects.requireNonNull(gateway);
                if ("HTTP".equalsIgnoreCase(gateway.getProtocol())) {
                    GatewayHttpConnection gatewayHttpConnection = new GatewayHttpConnection(
                            appProperties, jedisCluster, gateway,
                            errorCodeMappingConcurrentHashMap,
                            routingHashMap,
                            cdrProcessor);

                    httpConnectionManagerList.put(String.valueOf(gateway.getNetworkId()), gatewayHttpConnection);
                }
            });
            log.info("{} gateways loaded successfully", gatewaysMaps.size());
        } catch (Exception e) {
            log.error("Error on loadHttpConnectionManager: {}", e.getMessage());
        }
    }

    public void updateGateway(String stringNetworkId) {
        if (stringNetworkId != null) {
            String gatewayInRaw = jedisCluster.hget(this.appProperties.getKeyGatewayRedis(), stringNetworkId);
            if (gatewayInRaw == null) {
                log.warn("No gateway found for networkId {} on updateGateway", stringNetworkId);
                return;
            }
            Gateway gateway = Converter.stringToObject(gatewayInRaw, Gateway.class);
            Objects.requireNonNull(gateway);
            if (!"HTTP".equalsIgnoreCase(gateway.getProtocol())) {
                log.warn("This gateway {} is not handled by this application. Failed to update", stringNetworkId);
                return;
            }

            if (httpConnectionManagerList.containsKey(stringNetworkId)) {
                GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(stringNetworkId);
                gatewayHttpConnection.setGateway(gateway);
            } else {
                GatewayHttpConnection gatewayHttpConnection = new GatewayHttpConnection(
                        appProperties, jedisCluster, gateway,
                        errorCodeMappingConcurrentHashMap,
                        routingHashMap,
                        cdrProcessor);
                httpConnectionManagerList.put(stringNetworkId, gatewayHttpConnection);
            }
        } else {
            log.warn("No gateways found for connect on method updateGateway");
        }
    }

    public void connectGateway(String stringNetworkId) {
        if (stringNetworkId != null) {
            GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(stringNetworkId);
            if (Objects.isNull(gatewayHttpConnection)) { // Probably is an SMPP gateway trying to connect, this is not handled by this application
                log.warn("This gateway is not handled by this application, {}", stringNetworkId);
                return;
            }
            gatewayHttpConnection.connect();
            socketSession.sendStatus(stringNetworkId, Constants.PARAM_UPDATE_STATUS, "STARTED");
        } else {
            log.warn("No gateways found for connect on method connectGateway");
        }
    }

    public void stopGateway(String stringNetworkId) {
        if (stringNetworkId != null) {
            log.info("Stopping gateway with networkId {}", stringNetworkId);
            GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(stringNetworkId);
            if (Objects.isNull(gatewayHttpConnection)) {
                log.warn("The gateway with networkId {} is not handled by this application", stringNetworkId);
                return;
            }
            gatewayHttpConnection.getGateway().setStatus("STOPPED");
            socketSession.sendStatus(stringNetworkId, Constants.PARAM_UPDATE_STATUS, Constants.STOPPED);
        } else {
            log.warn("No gateways found for stop on method stopGateway");
        }
    }

    private void loadErrorCodeMapping() {
        try {
            var errorCodeMappingMap = jedisCluster.hgetAll(this.appProperties.getKeyErrorCodeMapping());
            if (errorCodeMappingMap.isEmpty()) {
                log.warn("No Error code mapping found on loadErrorCodeMapping");
                return;
            }

            errorCodeMappingMap.forEach((key, errorCodeMappingInRaw) -> {
                List<ErrorCodeMapping> errorCodeMappingList = Converter.stringToObject(errorCodeMappingInRaw, new TypeReference<>() {
                });
                errorCodeMappingConcurrentHashMap.put(key, errorCodeMappingList);
            });
            log.info("{} error code mapping loaded successfully", errorCodeMappingMap.size());
        } catch (Exception e) {
            log.error("Error on loadErrorCodeMapping: {}", e.getMessage());
        }
    }

    public void updateErrorCodeMapping(String mnoId) {
        if (mnoId == null || mnoId.isEmpty()) {
            log.warn("No Error code mapping found for mnoId null or empty");
            return;
        }

        String errorCodeMappingInRaw = jedisCluster.hget(this.appProperties.getKeyErrorCodeMapping(), mnoId);
        if (errorCodeMappingInRaw == null) {
            errorCodeMappingConcurrentHashMap.remove(mnoId); // Remove if existed, if not exist do anything
            return;
        }
        List<ErrorCodeMapping> errorCodeMappingList = Converter.stringToObject(errorCodeMappingInRaw, new TypeReference<>() {
        });
        errorCodeMappingConcurrentHashMap.put(mnoId, errorCodeMappingList); // Put do it the replacement if existed
    }

    public void deleteGateway(String stringNetworkId) {
        log.warn("Deleting gateway {}", stringNetworkId);
        GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(stringNetworkId);
        if (Objects.isNull(gatewayHttpConnection)) {
            log.warn("The gateway with networkId {} is not handled by this application. Failed to delete", stringNetworkId);
            return;
        }
        gatewayHttpConnection.getGateway().setStatus("STOPPED");
        httpConnectionManagerList.remove(stringNetworkId);
    }

    private void loadRoutingRules() {
        try {
            var routingRules = jedisCluster.hgetAll(this.appProperties.getKeyRoutingRules());
            if (routingRules.isEmpty()) {
                log.warn("No Routing Rules found on loadRoutingRules");
                return;
            }

            routingRules.forEach((key, value) -> {
                List<RoutingRule> routingList = Converter.stringToObject(value, new TypeReference<>() {
                });
                routingHashMap.put(Integer.parseInt(key), new ArrayList<>());
                routingList.forEach(r -> routingHashMap.get(r.getOriginNetworkId()).add(r));
            });
            log.info("{} routing rules loaded successfully", routingRules.size());
        } catch (Exception e) {
            log.error("Error loading routing rules: {}", e.getMessage());
        }
    }

    public void updateRoutingRules(String networkId) {
        var routingList = jedisCluster.hget(this.appProperties.getKeyRoutingRules(), networkId);
        if (routingList == null) {
            log.info("Error trying update routing rule for networkId {}", networkId);
            return;
        }

        List<RoutingRule> routingRulesToUpdate = Converter.stringToObject(routingList, new TypeReference<>() {
        });
        this.routingHashMap.put(Integer.parseInt(networkId), routingRulesToUpdate);
        log.info("Updated routing rules for network id {}: {}", networkId, routingRulesToUpdate.toArray());
    }

    public void deleteRoutingRules(String networkId) {
        if (networkId == null || networkId.isEmpty()) {
            log.warn("No Routing Rules found for null or empty id");
            return;
        }

        routingHashMap.remove(Integer.parseInt(networkId));
        log.info("Routing Rules with id {} deleted successfully", networkId);
    }
}
