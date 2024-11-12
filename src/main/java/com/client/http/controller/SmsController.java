package com.client.http.controller;

import com.client.http.dto.GlobalRecords;
import com.client.http.service.MessageEventService;
import com.paicbd.smsc.utils.Watcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@RestController
@RequiredArgsConstructor
public class SmsController {
    private final AtomicInteger requestPerSecond = new AtomicInteger(0);
    private final MessageEventService messageEventService;

    @PostConstruct
    public void init() {
        Thread.startVirtualThread(() -> new Watcher("HTTP-Submit-Received", requestPerSecond, 1));
    }

    @PostMapping("/message_delivery_receipt")
    public Mono<ResponseEntity<GlobalRecords.MessageResponse>> delivery(@RequestBody GlobalRecords.DlrRequest dlrRequest) {
        return Mono.fromCallable(() -> {
            requestPerSecond.incrementAndGet();
            GlobalRecords.MessageResponse response = messageEventService.processDelivery(dlrRequest);
            return ResponseEntity.ok(response);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
