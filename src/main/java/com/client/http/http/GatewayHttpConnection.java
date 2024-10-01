package com.client.http.http;

import com.client.http.dto.GlobalRecords;
import com.fasterxml.jackson.core.type.TypeReference;
import com.paicbd.smsc.cdr.CdrProcessor;
import com.paicbd.smsc.dto.MessageEvent;
import com.paicbd.smsc.dto.SubmitSmResponseEvent;
import com.paicbd.smsc.dto.RoutingRule;
import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.ErrorCodeMapping;
import com.paicbd.smsc.utils.Converter;
import com.paicbd.smsc.utils.UtilsEnum;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.utils.Watcher;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.util.DeliveryReceiptState;
import org.json.JSONObject;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class GatewayHttpConnection {
    private final ExecutorService mainExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ThreadFactory factory = Thread.ofVirtual().name("cache-", 0).factory();
    private final ExecutorService service = Executors.newThreadPerTaskExecutor(factory);
    private final AtomicInteger requestCounterTotal = new AtomicInteger(0);

    @Getter
    @Setter
    private Gateway gateway;

    private final String redisListName;
    private final AppProperties appProperties;
    private final JedisCluster jedisCluster;
    @Getter
    private final ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap;
    @Getter
    private final ConcurrentMap<Integer, List<RoutingRule>> routingHashMap;
    private final HttpClient httpClient;
    private final CdrProcessor cdrProcessor;

    public GatewayHttpConnection(AppProperties appProperties, JedisCluster jedisCluster, Gateway gateway,
                                 ConcurrentMap<String, List<ErrorCodeMapping>> errorCodeMappingConcurrentHashMap, ConcurrentMap<Integer, List<RoutingRule>> routingHashMap, CdrProcessor cdrProcessor) {
        this.appProperties = appProperties;
        this.jedisCluster = jedisCluster;
        this.errorCodeMappingConcurrentHashMap = errorCodeMappingConcurrentHashMap;
        this.gateway = gateway;
        this.routingHashMap = routingHashMap;
        this.cdrProcessor = cdrProcessor;
        this.redisListName = this.gateway.getNetworkId() + "_http_message";
        log.info("Redis List Name for {}, {}", gateway.getName(), this.redisListName);
        this.httpClient = appProperties.isHttp2() ? HttpClient.newHttpClient() : HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        Thread.startVirtualThread(() -> new Watcher("HTTP-Submit", requestCounterTotal, 1));
        this.processor();
    }

    private void processor() {
        CompletableFuture.runAsync(() -> Flux.interval(Duration.ofMillis(appProperties.getHttpJobExecuteEvery()))
                .onBackpressureBuffer()
                .flatMap(f -> {
                    boolean isGatewayEnabled = this.gateway.getEnabled() == 1;
                    boolean isGatewayStarted = "STARTED".equalsIgnoreCase(this.gateway.getStatus());
                    if (isGatewayEnabled && isGatewayStarted) {
                        return fetchAllItems()
                                .filter(Objects::nonNull)
                                .doOnNext(this::sendSubmitSmList);
                    }
                    return Flux.empty();
                })
                .subscribe(), mainExecutor);
    }

    public void connect() {
        log.info("Connected to Gateway {}", this.gateway.getName());
        this.gateway.setStatus("STARTED");
        this.gateway.setEnabled(1);
        jedisCluster.hset(this.appProperties.getKeyGatewayRedis(), gateway.getSystemId(), this.gateway.toString());
    }

    public Flux<List<String>> fetchAllItems() {
        int batchSize = batchPerWorker();
        if (batchSize <= 0) {
            return Flux.empty();
        }
        return Flux.range(0, appProperties.getHttpWorkersPerGw())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(worker -> {
                    List<String> batch = jedisCluster.lpop(redisListName, batchSize);
                    if (Objects.isNull(batch) || batch.isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.just(batch);
                }).subscribeOn(Schedulers.boundedElastic());
    }

    private HttpRequest newRequest(String body) {
        return HttpRequest.newBuilder()
                .uri(URI.create(this.gateway.getIp()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofMillis(this.gateway.getPduTimeout()))
                .build();
    }

    private int batchPerWorker() {
        int listSize = (int) jedisCluster.llen(redisListName);
        if (listSize == 0) {
            return 0;
        }

        int recordsToTake = appProperties.getHttpRecordsPerGw() * (appProperties.getHttpJobExecuteEvery() / 1000);
        int min = Math.min(recordsToTake, listSize);
        var bpw = min / appProperties.getHttpWorkersPerGw();
        return bpw > 0 ? bpw : 1;
    }

    public void sendToRetryProcess(MessageEvent submitSmEventToRetry, int errorCode) {
        log.warn("Starting retry process for submit_sm with id {} and error code {}", submitSmEventToRetry.getMessageId(), errorCode);
        if (errorContained(gateway.getNoRetryErrorCode(), errorCode)) {
            handleNoRetryError(submitSmEventToRetry, errorCode);
            return;
        }

        if (errorContained(gateway.getRetryAlternateDestinationErrorCode(), errorCode)) {
            handleRetryAlternateDestination(submitSmEventToRetry, errorCode);
            return;
        }

        handleAutoRetry(submitSmEventToRetry, errorCode);
    }

    private void handleNoRetryError(MessageEvent submitSmEventToRetry, int errorCode) {
        log.warn("Failed to retry for submit_sm with id {}. The gateway {} contains this error code for no retry", submitSmEventToRetry.getMessageId(), this.getGateway().getName());
        sendDeliverSm(submitSmEventToRetry, errorCode);
        handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "ERROR IS DEFINED AS NO RETRY");
    }

    private void handleRetryAlternateDestination(MessageEvent submitSmEventToRetry, int errorCode) {
        Map.Entry<Integer, String> alternativeRoute = getAlternativeRoute(submitSmEventToRetry);
        if (alternativeRoute != null) {
            prepareForRetry(submitSmEventToRetry, alternativeRoute);
            String listName = determineListName(submitSmEventToRetry.getDestProtocol(), submitSmEventToRetry.getDestNetworkId());
            if (Objects.nonNull(listName)) {
                log.warn("Retry for submit_sm with id {}. new networkId {}", submitSmEventToRetry.getMessageId(), submitSmEventToRetry.getDestNetworkId());
                jedisCluster.rpush(listName, submitSmEventToRetry.toString());
                handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "MESSAGE HAS BEEN SENT TO ALTERNATIVE ROUTE");
                return;
            }
            log.warn("Failed to retry for submit_sm with id {}. No more alternative routes were found", submitSmEventToRetry.getMessageId());
            handleNoAlternativeRoute(submitSmEventToRetry, errorCode, "ALTERNATIVE ROUTE FOUND BUT LIST NAME IS NULL ON HTTP FOR ERROR CODE " + errorCode);
        } else {
            log.warn("Failed to retry for submit_sm with id {}. No more alternative routes were found on else block", submitSmEventToRetry.getMessageId());
            handleNoAlternativeRoute(submitSmEventToRetry, errorCode, "NO ALTERNATIVE ROUTES WERE FOUND ON HTTP FOR ERROR CODE " + errorCode);
        }
    }

    private void handleNoAlternativeRoute(MessageEvent submitSmEventToRetry, int errorCode, String message) {
        sendDeliverSm(submitSmEventToRetry, errorCode);
        handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, message);
    }

    private void handleAutoRetry(MessageEvent submitSmEventToRetry, int errorCode) {
        log.info("AutoRetryErrorCodes defined {}, ReceivedErrorCode {}", gateway.getAutoRetryErrorCode(), errorCode);
        if (errorContained(gateway.getAutoRetryErrorCode(), errorCode)) {
            log.info("AutoRetrying for submit_sm with id {}", submitSmEventToRetry.getMessageId());
            handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "MESSAGE SENT TO AUTO RETRY PROCESS DUE HTTP ERROR " + errorCode);
            submitSmEventToRetry.setRetryNumber(
                    Objects.isNull(submitSmEventToRetry.getRetryNumber()) ? 1 : submitSmEventToRetry.getRetryNumber() + 1
            );
            jedisCluster.lpush(appProperties.getRetryMessageQueue(), submitSmEventToRetry.toString());
            log.info("Successfully added to Redis retry message list {} -> {}", appProperties.getRetryMessageQueue(), submitSmEventToRetry);
            return;
        }

        log.warn("Failed to retry for submit_sm with id {}. The gateway {} doesn't have the error code {} for the retry process.", submitSmEventToRetry.getMessageId(), this.getGateway().getName(), errorCode);
        sendDeliverSm(submitSmEventToRetry, errorCode);
        handlerCdrDetail(submitSmEventToRetry, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "NOT FOUND ERROR CODE TO AUTO RETRY");
    }

    private void prepareForRetry(MessageEvent submitSmEventToRetry, Map.Entry<Integer, String> alternativeRoute) {
        submitSmEventToRetry.setRetry(true);
        String existingRetryDestNetworkId = submitSmEventToRetry.getRetryDestNetworkId();
        if (existingRetryDestNetworkId.isEmpty()) {
            submitSmEventToRetry.setRetryDestNetworkId(submitSmEventToRetry.getDestNetworkId() + "");
        } else {
            submitSmEventToRetry.setRetryDestNetworkId(existingRetryDestNetworkId + "," + submitSmEventToRetry.getDestNetworkId());
        }
        submitSmEventToRetry.setDestNetworkId(alternativeRoute.getKey());
        submitSmEventToRetry.setDestProtocol(alternativeRoute.getValue());
    }

    private String determineListName(String destProtocol, Integer destNetworkId) {
        return switch (destProtocol.toLowerCase()) {
            case "http" -> destNetworkId + "_http_message";
            case "smpp" -> destNetworkId + "_smpp_message";
            case "ss7" -> destNetworkId + "_ss7_message";
            default -> {
                log.error("Invalid Destination Protocol");
                yield null;
            }
        };
    }

    public void handlingLastRetry(MessageEvent submitSmEvent, int errorCode) {
        log.info("Last retry for submit_sm with id {}, the message will be sent to DLR", submitSmEvent.getMessageId());
        handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "VALIDITY PERIOD WILL BE EXCEEDED FOR NEXT RETRY");
        sendDeliverSm(submitSmEvent, errorCode);
    }

    public boolean errorContained(String stringList, int errorCode) {
        return Arrays.stream(stringList.split(","))
                .map(String::trim)
                .anyMatch(code -> code.equals(String.valueOf(errorCode)));
    }

    private Map.Entry<Integer, String> getAlternativeRoute(MessageEvent submitSmEventToRetry) {
        List<RoutingRule> routingList = this.routingHashMap.get(submitSmEventToRetry.getOriginNetworkId());
        if (routingList != null && !routingList.isEmpty()) {
            Optional<RoutingRule> optionalRouting = routingList.stream().filter(routing -> routing.getId() == submitSmEventToRetry.getRoutingId()).findFirst();
            if (optionalRouting.isPresent()) {
                List<RoutingRule.Destination> orderDestinations = optionalRouting.get().getDestination().stream()
                        .sorted(Comparator.comparingInt(RoutingRule.Destination::getPriority)).toList();
                for (RoutingRule.Destination destination : orderDestinations) {
                    if (destination.getNetworkId() != submitSmEventToRetry.getDestNetworkId()
                            && !errorContained(submitSmEventToRetry.getRetryDestNetworkId(), destination.getNetworkId())) {
                        return Map.entry(destination.getNetworkId(), destination.getProtocol());
                    }
                }
            }
        }
        return null;
    }

    private void sendDeliverSm(MessageEvent submitSmEventToRetry, int errorCode) {
        MessageEvent deliverSmEvent = createDeliverSm(submitSmEventToRetry, errorCode);
        switch (submitSmEventToRetry.getOriginProtocol().toUpperCase()) {
            case "HTTP" -> jedisCluster.rpush("http_dlr", deliverSmEvent.toString());
            case "SMPP" -> jedisCluster.rpush("smpp_dlr", deliverSmEvent.toString());
            default -> log.error("Invalid Origin Protocol");
        }
        handlerCdrDetail(deliverSmEvent, UtilsEnum.MessageType.DELIVER, UtilsEnum.CdrStatus.FAILED, cdrProcessor, false, "MESSAGE FOR HTTP GATEWAY FAILED, SENDING DLR");
    }

    private MessageEvent createDeliverSm(MessageEvent submitSmEventToRetry, int errorCode) {
        MessageEvent deliverSmEvent = new MessageEvent();
        DeliveryReceiptState deliveryReceiptState = DeliveryReceiptState.UNDELIV;
        String shortMessage = submitSmEventToRetry.getShortMessage();
        DeliveryReceipt delRec = new DeliveryReceipt(submitSmEventToRetry.getMessageId(), 1, 0,
                new Date(), new Date(), deliveryReceiptState, "34", shortMessage != null ? shortMessage : "");
        deliverSmEvent.setStatus("UNDELIV");

        List<ErrorCodeMapping> errorCodeMappingList = errorCodeMappingConcurrentHashMap.get(String.valueOf(gateway.getMno()));
        if (errorCodeMappingList != null) {
            Optional<ErrorCodeMapping> optionalErrorCodeMapping = errorCodeMappingList.stream().filter(errorCodeMapping -> errorCodeMapping.getErrorCode() == errorCode).findFirst();
            if (optionalErrorCodeMapping.isPresent()) {
                deliveryReceiptState = UtilsEnum.getDeliverReceiptState(optionalErrorCodeMapping.get().getDeliveryStatus());
                String error = optionalErrorCodeMapping.get().getDeliveryErrorCode() + "";
                delRec.setFinalStatus(deliveryReceiptState);
                delRec.setError(error);
                deliverSmEvent.setStatus(deliveryReceiptState.toString());
                log.warn("Creating deliver_sm with status {} and error {} for submit_sm with id {}", deliveryReceiptState, error, submitSmEventToRetry.getMessageId());
            } else {
                log.warn("No error code mapping found for mno {} with error {}. using status {}", gateway.getMno(), errorCode, DeliveryReceiptState.UNDELIV);
            }
        } else {
            log.warn("No error code mapping found for mno {} using status {}", gateway.getMno(), DeliveryReceiptState.UNDELIV);
        }

        deliverSmEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
        deliverSmEvent.setMessageId(submitSmEventToRetry.getMessageId());
        deliverSmEvent.setSystemId(submitSmEventToRetry.getSystemId());
        deliverSmEvent.setCommandStatus(submitSmEventToRetry.getCommandStatus());
        deliverSmEvent.setSequenceNumber(submitSmEventToRetry.getSequenceNumber());
        deliverSmEvent.setSourceAddrTon(submitSmEventToRetry.getDestAddrTon());
        deliverSmEvent.setSourceAddrNpi(submitSmEventToRetry.getDestAddrNpi());
        deliverSmEvent.setSourceAddr(submitSmEventToRetry.getDestinationAddr());
        deliverSmEvent.setDestAddrTon(submitSmEventToRetry.getSourceAddrTon());
        deliverSmEvent.setDestAddrNpi(submitSmEventToRetry.getSourceAddrNpi());
        deliverSmEvent.setDestinationAddr(submitSmEventToRetry.getSourceAddr());
        deliverSmEvent.setEsmClass(submitSmEventToRetry.getEsmClass());
        deliverSmEvent.setValidityPeriod(submitSmEventToRetry.getValidityPeriod());
        deliverSmEvent.setRegisteredDelivery(submitSmEventToRetry.getRegisteredDelivery());
        deliverSmEvent.setDataCoding(submitSmEventToRetry.getDataCoding());
        deliverSmEvent.setSmDefaultMsgId(submitSmEventToRetry.getSmDefaultMsgId());
        deliverSmEvent.setCheckSubmitSmResponse(false);
        deliverSmEvent.setDeliverSmServerId(submitSmEventToRetry.getMessageId());
        deliverSmEvent.setMessageId(submitSmEventToRetry.getMessageId());
        String dlrMessage = delRec.toString();
        deliverSmEvent.setShortMessage(dlrMessage);
        deliverSmEvent.setDelReceipt(dlrMessage);
        deliverSmEvent.setOriginNetworkType(submitSmEventToRetry.getDestNetworkType());
        deliverSmEvent.setOriginProtocol(submitSmEventToRetry.getDestProtocol());
        deliverSmEvent.setOriginNetworkId(submitSmEventToRetry.getDestNetworkId());
        deliverSmEvent.setDestNetworkType(submitSmEventToRetry.getOriginNetworkType());
        deliverSmEvent.setDestProtocol(submitSmEventToRetry.getOriginProtocol());
        deliverSmEvent.setDestNetworkId(submitSmEventToRetry.getOriginNetworkId());
        deliverSmEvent.setRoutingId(submitSmEventToRetry.getRoutingId());

        return deliverSmEvent;
    }

    public void sendSubmitSmList(List<String> events) {
        Flux.fromIterable(events)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(this::handleSubmitSm)
                .subscribe();
    }

    private void handleSubmitSm(String submitSmRaw) {
        MessageEvent submitSmEvent = Converter.stringToObject(submitSmRaw, MessageEvent.class);
        if (submitSmEvent.getMessageParts() != null) {
            submitSmEvent.getMessageParts().forEach(msgPart -> {
                var messageEvent = new MessageEvent().clone(submitSmEvent);
                messageEvent.setMessageId(msgPart.getMessageId());
                messageEvent.setShortMessage(msgPart.getShortMessage());
                messageEvent.setMsgReferenceNumber(msgPart.getMsgReferenceNumber());
                messageEvent.setTotalSegment(msgPart.getTotalSegment());
                messageEvent.setSegmentSequence(msgPart.getSegmentSequence());
                messageEvent.setUdhJson(msgPart.getUdhJson());
                messageEvent.setOptionalParameters(msgPart.getOptionalParameters());
                messageEvent.setMessageParts(null);
                sendMessage(messageEvent);
            });
        } else {
            sendMessage(submitSmEvent);
        }
    }


    private void sendMessage(MessageEvent submitSmEvent) {
        var requestBody = messageEventToRequest(submitSmEvent);
        if (requestBody == null)
            return;

        HttpRequest request = newRequest(requestBody);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                service.execute(() -> addInCache(submitSmEvent, response));
                handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.SENT, cdrProcessor, true, "Sent to GW");
            } else {
                if (submitSmEvent.isLastRetry()) {
                    handlingLastRetry(submitSmEvent, response.statusCode());
                    return;
                }
                sendToRetryProcess(submitSmEvent, response.statusCode());
            }
        } catch (HttpTimeoutException e) {
            log.error("An exception occurred trying to send http request due to HttpTimeoutException -> {}", e.getMessage());
            if (submitSmEvent.isLastRetry()) {
                handlingLastRetry(submitSmEvent, 408);
                return;
            }
            sendToRetryProcess(submitSmEvent, 408);
        } catch (IOException | InterruptedException e) {
            log.error("An exception occurred trying to send http request -> {}", e.getMessage());
            handlerCdrDetail(submitSmEvent, UtilsEnum.MessageType.MESSAGE, UtilsEnum.CdrStatus.FAILED, cdrProcessor, true, "AN EXCEPTION OCCURRED TRYING TO SEND HTTP REQUEST");
            Thread.currentThread().interrupt();
        }
    }

    private String messageEventToRequest(MessageEvent messageEvent) {
        var messageRequest = new GlobalRecords.MessageRequest(
                messageEvent.getMessageId(),
                messageEvent.getSourceAddrTon(),
                messageEvent.getSourceAddrNpi(),
                messageEvent.getSourceAddr(),
                messageEvent.getDestAddrTon(),
                messageEvent.getDestAddrNpi(),
                messageEvent.getDestinationAddr(),
                messageEvent.getRegisteredDelivery(),
                messageEvent.getDataCoding(),
                messageEvent.getShortMessage(),
                messageEvent.getOptionalParameters()
        );
        return messageRequest.toString();
    }

    private void addInCache(MessageEvent submitSmEvent, HttpResponse<String> response) {
        JSONObject jsonObj = new JSONObject(response.body());
        Map<String, Object> responseMap = Converter.stringToObject(jsonObj.toString(), new TypeReference<>() {
        });
        if (submitSmEvent.getRegisteredDelivery() != 0) {
            if (responseMap.containsKey("message_id")) {
                var id = responseMap.get("message_id").toString();
                log.debug("Requesting DLR for submit_sm with id {} and messageId {}", submitSmEvent.getId(), id);
                SubmitSmResponseEvent submitSmResponseEvent = new SubmitSmResponseEvent();
                submitSmResponseEvent.setHashId(id);
                submitSmResponseEvent.setSystemId(submitSmEvent.getSystemId());
                submitSmResponseEvent.setId(System.currentTimeMillis() + "-" + System.nanoTime());
                submitSmResponseEvent.setSubmitSmId(id);
                submitSmResponseEvent.setSubmitSmServerId(submitSmEvent.getMessageId());
                submitSmResponseEvent.setOriginProtocol(submitSmEvent.getOriginProtocol().toUpperCase());
                submitSmResponseEvent.setOriginNetworkId(submitSmEvent.getOriginNetworkId());
                submitSmResponseEvent.setParentId(submitSmEvent.getParentId());
                jedisCluster.hset(appProperties.getSubmitSmResultQueue(), submitSmResponseEvent.getHashId(), submitSmResponseEvent.toString());
                requestCounterTotal.incrementAndGet();
                return;
            }
            log.warn("The response body doesn't contains message_id {} ", response);
        }
    }

    private static void handlerCdrDetail(
            MessageEvent deliverSmEvent, UtilsEnum.MessageType messageType, UtilsEnum.CdrStatus cdrStatus,
            CdrProcessor cdrProcessor, boolean createCdr, String message) {
        cdrProcessor.putCdrDetailOnRedis(
                deliverSmEvent.toCdrDetail(UtilsEnum.Module.HTTP_CLIENT, messageType, cdrStatus, message));
        if (createCdr) {
            cdrProcessor.createCdr(deliverSmEvent.getMessageId());
        }
    }
}
