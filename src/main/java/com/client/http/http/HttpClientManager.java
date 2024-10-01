package com.client.http.http;

import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.ServiceProvider;
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
    private final ConcurrentMap<String, ServiceProvider> serviceProvidersConcurrentHashMap;
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    private final ConcurrentMap<Integer, List<RoutingRule>> routingHashMap;

    private final CdrProcessor cdrProcessor;

    @PostConstruct
    public void startManager() {
        loadHttpConnectionManager();
        loadErrorCodeMapping();
        loadServiceProviders();
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
                Gateway gateway = Converter.stringToObject(gatewayInRaw, new TypeReference<>() {
                });
                if ("HTTP".equalsIgnoreCase(gateway.getProtocol())) {
                    GatewayHttpConnection gatewayHttpConnection = new GatewayHttpConnection(
                            appProperties, jedisCluster, gateway,
                            errorCodeMappingConcurrentHashMap,
                            routingHashMap,
                            cdrProcessor);

                    httpConnectionManagerList.put(gateway.getSystemId(), gatewayHttpConnection);
                }
            });
            log.info("{} gateways loaded successfully", gatewaysMaps.size());
        } catch (Exception e) {
            log.error("Error on loadHttpConnectionManager: {}", e.getMessage());
        }
    }

    public void updateGateway(String systemId) {
        if (systemId != null) {
            String gatewayInRaw = jedisCluster.hget(this.appProperties.getKeyGatewayRedis(), systemId);
            if (gatewayInRaw == null) {
                log.warn("No gateways found for connect on updateGateway");
                return;
            }
            Gateway gateway = Converter.stringToObject(gatewayInRaw, new TypeReference<>() {
            });
            if (Objects.isNull(gateway.getSystemId())) {
                log.warn("Gateway not found on redis");
                return;
            }
            if (!"HTTP".equalsIgnoreCase(gateway.getProtocol())) {
                log.warn("This gateway {} is not handled by this application. Failed to update", systemId);
                return;
            }

            if (httpConnectionManagerList.containsKey(systemId)) {
                GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(systemId);
                gatewayHttpConnection.setGateway(gateway);
            } else {
                GatewayHttpConnection gatewayHttpConnection = new GatewayHttpConnection(
                        appProperties, jedisCluster, gateway,
                        errorCodeMappingConcurrentHashMap,
                        routingHashMap,
                        cdrProcessor);
                httpConnectionManagerList.put(gateway.getSystemId(), gatewayHttpConnection);
            }
        } else {
            log.warn("No gateways found for connect on method updateGateway");
        }
    }

    public void connectGateway(String systemId) {
        if (systemId != null) {
            GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(systemId);
            if (Objects.isNull(gatewayHttpConnection)) { // Probably is an HTTP gateway trying to connect, this is not handled by this application
                log.warn("This gateway is not handled by this application, {}", systemId);
                return;
            }
            try {
                gatewayHttpConnection.connect();
                socketSession.sendStatus(systemId, Constants.PARAM_UPDATE_STATUS, "STARTED");
            } catch (Exception e) {
                log.error("Error on connect on gateway {} with error {}", systemId, e.getMessage());
            }
        } else {
            log.warn("No gateways found for connect on method connectGateway");
        }
    }

    public void stopGateway(String systemId) {
        if (systemId != null) {
            log.info("Stopping gateway {}", systemId);
            GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(systemId);
            if (Objects.isNull(gatewayHttpConnection)) {
                log.warn("The gateway {} is not handled by this application", systemId);
                return;
            }
            try {
                gatewayHttpConnection.getGateway().setStatus("STOPPED");
                socketSession.sendStatus(systemId, Constants.PARAM_UPDATE_STATUS, Constants.STOPPED);
            } catch (Exception e) {
                log.error("Error on stop on gateway {} with error {}", systemId, e.getMessage());
            }
        } else {
            log.warn("No gateways found for stop on method stopGateway");
        }
    }

    private void loadServiceProviders() {
        try {
            var serviceProviderMap = this.jedisCluster.hgetAll(this.appProperties.getKeyServiceProvidersRedis());
            if (serviceProviderMap.isEmpty()) {
                log.warn("No service Providers found on loadServiceProviders");
                return;
            }
            serviceProviderMap.values().forEach(serviceProviderInRaw -> {
                ServiceProvider serviceProvider = Converter.stringToObject(serviceProviderInRaw, new TypeReference<>() {
                });
                if ("HTTP".equalsIgnoreCase(serviceProvider.getProtocol())) {
                    serviceProvidersConcurrentHashMap.put(serviceProvider.getSystemId(), serviceProvider);
                }
            });
            log.info("{} service providers loaded successfully", serviceProviderMap.size());
        } catch (Exception e) {
            log.error("Error on loadServiceProviders: {}", e.getMessage());
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
                List<ErrorCodeMapping> errorCodeMappingList = Converter.stringToObject(errorCodeMappingInRaw, new TypeReference<>() {});
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

    public void deleteGateway(String systemId) {
        log.warn("Deleting gateway {}", systemId);
        GatewayHttpConnection gatewayHttpConnection = httpConnectionManagerList.get(systemId);
        if (Objects.isNull(gatewayHttpConnection)) {
            log.warn("The gateway {} is not handled by this application. Failed to delete", systemId);
            return;
        }
        gatewayHttpConnection.getGateway().setStatus("STOPPED");
        httpConnectionManagerList.remove(systemId);
    }

    public void updateServiceProvider(String systemId) {
        if (systemId != null) {
            String serviceProvidersInRaw = jedisCluster.hget(this.appProperties.getKeyServiceProvidersRedis(), systemId);
            if (serviceProvidersInRaw == null) {
                log.warn("No service providers found for update on updateServiceProvider");
                return;
            }
            ServiceProvider serviceProvider = Converter.stringToObject(serviceProvidersInRaw, new TypeReference<>() {
            });
            if (!"HTTP".equalsIgnoreCase(serviceProvider.getProtocol())) {
                log.warn("This service provider {} is not handled by this application. Failed to update", systemId);
                return;
            }
            serviceProvidersConcurrentHashMap.put(systemId, serviceProvider);
            log.info("Updated on redis service provider: {}", serviceProvider.toString());
        } else {
            log.warn("No service providers found for update on method updateServiceProvider");
        }
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

        try {
            routingHashMap.remove(Integer.parseInt(networkId));
            log.info("Routing Rules with id {} deleted successfully", networkId);
        } catch (Exception ex) {
            log.error("Error while deleting routing rules with id {}: {}", networkId, ex.getMessage());
        }
    }
}
