package com.client.http.utils;

import com.paicbd.smsc.utils.Generated;

@Generated
public class Constants {
    private Constants() {
        throw new IllegalStateException("Utility class");
    }

    public static final String STOPPED = "STOPPED";
    public static final String UPDATE_GATEWAY_ENDPOINT = "/app/http/updateGateway";
    public static final String CONNECT_GATEWAY_ENDPOINT = "/app/http/connectGateway";
    public static final String RESPONSE_SMPP_CLIENT_ENDPOINT = "/app/response-smpp-client";
    public static final String STOP_GATEWAY_ENDPOINT = "/app/http/stopGateway";
    public static final String DELETE_GATEWAY_ENDPOINT = "/app/http/deleteGateway";
    public static final String UPDATE_ERROR_CODE_MAPPING_ENDPOINT = "/app/updateErrorCodeMapping"; // Receive mno_id as String
    public static final String UPDATE_ROUTING_RULES_ENDPOINT = "/app/update/routingRules";
    public static final String DELETE_ROUTING_RULES_ENDPOINT = "/app/delete/routingRules";
    public static final String PARAM_UPDATE_STATUS = "status";
    public static final int IS_STARTED = 1;
}
