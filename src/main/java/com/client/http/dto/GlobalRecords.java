package com.client.http.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import jakarta.annotation.Nonnull;

import java.util.List;

public class GlobalRecords {

    public record DlrRequest(
            @Nonnull @JsonProperty("message_id") String messageId,
            @JsonProperty("source_addr_ton") int sourceAddrTon,
            @JsonProperty("source_addr_npi") int sourceAddrNpi,
            @JsonProperty("source_addr") String sourceAddr,
            @JsonProperty("dest_addr_ton") int destAddrTon,
            @JsonProperty("dest_addr_npi") int destAddrNpi,
            @JsonProperty("destination_addr") String destinationAddr,
            @JsonProperty("data_coding") Integer dataCoding,
            @JsonProperty("status") String status,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("optional_parameters") List<UtilsRecords.OptionalParameter> optionalParameters
    ) {

        @Override
        public String toString() {
            return Converter.valueAsString(this);
        }
    }

    public record MessageRequest(
            @Nonnull @JsonProperty("message_id") String messageId,
            @JsonProperty("source_addr_ton") int sourceAddrTon,
            @JsonProperty("source_addr_npi") int sourceAddrNpi,
            @JsonProperty("source_addr") String sourceAddr,
            @JsonProperty("dest_addr_ton") int destAddrTon,
            @JsonProperty("dest_addr_npi") int destAddrNpi,
            @JsonProperty("destination_addr") String destinationAddr,
            @JsonProperty("registered_delivery") Integer registeredDelivery,
            @JsonProperty("data_coding") Integer dataCoding,
            @JsonProperty("short_message") String shortMessage,
            @JsonProperty("optional_parameters") List<UtilsRecords.OptionalParameter> optionalParameters
    ) {

        @Override
        public String toString() {
            return Converter.valueAsString(this);
        }
    }

    public record MessageResponse(
            @JsonProperty("system_id") String systemId,
            @JsonProperty("message_id") String messageId,
            @JsonProperty("error_message") String errorMessage) {

        public MessageResponse(String systemId, String messageId) {
            this(systemId, messageId, null);
        }

        public MessageResponse(String errorMessage) {
            this(null, null, errorMessage);
        }
    }
}
