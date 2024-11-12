package com.client.http.service;

import java.util.Objects;

import com.client.http.dto.GlobalRecords;
import com.client.http.utils.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisCluster;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEventService {
	private final JedisCluster jedisCluster;
    private final AppProperties appProperties;

	public GlobalRecords.MessageResponse processDelivery(GlobalRecords.DlrRequest dlrRequest) {
		String existSubmitResp = jedisCluster.hget(appProperties.getSubmitSmResultQueue(), dlrRequest.messageId().toUpperCase());
		if (Objects.isNull(existSubmitResp)) {
			log.warn("No DLR sent, messageId not found");
			return new GlobalRecords.MessageResponse("No DLR sent, messageId not found");
		}

		jedisCluster.rpush("http_dlr_request", dlrRequest.toString());
		return new GlobalRecords.MessageResponse("DLR sent successfully");
	}
}
