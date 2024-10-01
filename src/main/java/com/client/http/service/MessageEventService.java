package com.client.http.service;

import java.util.Objects;
import java.util.concurrent.ConcurrentMap;

import com.client.http.dto.GlobalRecords;
import com.client.http.utils.AppProperties;
import com.paicbd.smsc.utils.SmppEncoding;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import com.paicbd.smsc.dto.Gateway;
import com.paicbd.smsc.dto.MessageEvent;
import com.client.http.http.GatewayHttpConnection;

import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisCluster;
import static com.client.http.utils.Constants.ORIGIN_GATEWAY_TYPE;
import static com.client.http.utils.Constants.IS_STARTED;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageEventService {
	private final JedisCluster jedisCluster;
    private final AppProperties appProperties;
	private final ConcurrentMap<String, GatewayHttpConnection> httpConnectionManagerList;

	public GlobalRecords.MessageResponse processDelivery(GlobalRecords.DlrRequest dlrRequest) {
		String existSubmitResp = jedisCluster.hget(appProperties.getSubmitSmResultQueue(), dlrRequest.messageId());
		if (Objects.isNull(existSubmitResp)) {
			return new GlobalRecords.MessageResponse("No DLR sent, messageId not found");
		}

		jedisCluster.rpush("http_dlr_request", dlrRequest.toString());
		return new GlobalRecords.MessageResponse("DLR sent successfully");
	}

	public GlobalRecords.MessageResponse validateAndProcessMessage(MessageEvent pdu) {
		if (!SmppEncoding.isValidDataCoding(pdu.getDataCoding())) {
			return new GlobalRecords.MessageResponse("Invalid data coding");
		}

		GatewayHttpConnection gateways = httpConnectionManagerList.getOrDefault(pdu.getSystemId(), null);
		boolean enabled = gateways.getGateway().getEnabled() == IS_STARTED;
		if (gateways.getGateway() == null || !enabled) {
			return new GlobalRecords.MessageResponse("Service not found");
		}

		Gateway gateway = gateways.getGateway();
		pdu.setId(System.currentTimeMillis() + "-" + System.nanoTime());
		pdu.setOriginNetworkId(gateway.getNetworkId());
		pdu.setOriginNetworkType(ORIGIN_GATEWAY_TYPE);
		pdu.setOriginProtocol(gateway.getProtocol());

		jedisCluster.lpush(appProperties.getPreDeliverQueue(), pdu.toString());
		return new GlobalRecords.MessageResponse(pdu.getSystemId(), pdu.getId());
	}
}
