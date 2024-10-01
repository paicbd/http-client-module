package com.client.http.dto;

import com.client.http.dto.GlobalRecords.DlrRequest;
import com.client.http.dto.GlobalRecords.MessageRequest;
import com.client.http.dto.GlobalRecords.MessageResponse;
import com.paicbd.smsc.dto.UtilsRecords;
import com.paicbd.smsc.utils.Converter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlobalRecordsTest {

    @Test
    void testGlobalRecords() {
        GlobalRecords globalRecords = new GlobalRecords();
        assertNotNull(globalRecords);
    }

    @Test
    void testOptionalParameter_record() {
        short tag = 1;
        String value = "testValue";
        UtilsRecords.OptionalParameter optionalParameter = new UtilsRecords.OptionalParameter(tag, value);

        assertEquals(tag, optionalParameter.tag());
        assertEquals(value, optionalParameter.value());
    }

    @Test
    void testDlrRequest_record() {
        String messageId = "msg123";
        int sourceAddrTon = 1;
        int sourceAddrNpi = 1;
        String sourceAddr = "sourceAddr";
        int destAddrTon = 1;
        int destAddrNpi = 1;
        String destinationAddr = "destAddr";
        Integer dataCoding = 1;
        String status = "status";
        String errorCode = "errorCode";
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(new UtilsRecords.OptionalParameter((short) 1, "value"));

        DlrRequest dlrRequest = new DlrRequest(
                messageId, sourceAddrTon, sourceAddrNpi, sourceAddr,
                destAddrTon, destAddrNpi, destinationAddr, dataCoding,
                status, errorCode, optionalParameters
        );

        assertEquals(messageId, dlrRequest.messageId());
        assertEquals(sourceAddrTon, dlrRequest.sourceAddrTon());
        assertEquals(sourceAddrNpi, dlrRequest.sourceAddrNpi());
        assertEquals(sourceAddr, dlrRequest.sourceAddr());
        assertEquals(destAddrTon, dlrRequest.destAddrTon());
        assertEquals(destAddrNpi, dlrRequest.destAddrNpi());
        assertEquals(destinationAddr, dlrRequest.destinationAddr());
        assertEquals(dataCoding, dlrRequest.dataCoding());
        assertEquals(status, dlrRequest.status());
        assertEquals(errorCode, dlrRequest.errorCode());
        assertEquals(optionalParameters, dlrRequest.optionalParameters());
    }

    @Test
    void testMessageRequest_record() {
        String messageId = "msg123";
        int sourceAddrTon = 1;
        int sourceAddrNpi = 1;
        String sourceAddr = "sourceAddr";
        int destAddrTon = 1;
        int destAddrNpi = 1;
        String destinationAddr = "destAddr";
        Integer registeredDelivery = 1;
        Integer dataCoding = 1;
        String shortMessage = "shortMessage";
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(new UtilsRecords.OptionalParameter((short) 1, "value"));

        MessageRequest messageRequest = new MessageRequest(
                messageId, sourceAddrTon, sourceAddrNpi, sourceAddr,
                destAddrTon, destAddrNpi, destinationAddr, registeredDelivery,
                dataCoding, shortMessage, optionalParameters
        );

        assertEquals(messageId, messageRequest.messageId());
        assertEquals(sourceAddrTon, messageRequest.sourceAddrTon());
        assertEquals(sourceAddrNpi, messageRequest.sourceAddrNpi());
        assertEquals(sourceAddr, messageRequest.sourceAddr());
        assertEquals(destAddrTon, messageRequest.destAddrTon());
        assertEquals(destAddrNpi, messageRequest.destAddrNpi());
        assertEquals(destinationAddr, messageRequest.destinationAddr());
        assertEquals(registeredDelivery, messageRequest.registeredDelivery());
        assertEquals(dataCoding, messageRequest.dataCoding());
        assertEquals(shortMessage, messageRequest.shortMessage());
        assertEquals(optionalParameters, messageRequest.optionalParameters());
    }

    @Test
    void testMessageResponse_record_withSystemIdAndMessageId() {
        String systemId = "system123";
        String messageId = "message123";
        MessageResponse messageResponse = new MessageResponse(systemId, messageId);

        assertEquals(systemId, messageResponse.systemId());
        assertEquals(messageId, messageResponse.messageId());
        assertNull(messageResponse.errorMessage());
    }

    @Test
    void testMessageResponse_record_withErrorMessage() {
        String errorMessage = "Error occurred";
        MessageResponse messageResponse = new MessageResponse(errorMessage);

        assertNull(messageResponse.systemId());
        assertNull(messageResponse.messageId());
        assertEquals(errorMessage, messageResponse.errorMessage());
    }

    @Test
    void testDlrRequest_toString() {
        String messageId = "msg123";
        int sourceAddrTon = 1;
        int sourceAddrNpi = 1;
        String sourceAddr = "sourceAddr";
        int destAddrTon = 1;
        int destAddrNpi = 1;
        String destinationAddr = "destAddr";
        Integer dataCoding = 1;
        String status = "status";
        String errorCode = "errorCode";
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(new UtilsRecords.OptionalParameter((short) 1, "value"));

        DlrRequest dlrRequest = new DlrRequest(
                messageId, sourceAddrTon, sourceAddrNpi, sourceAddr,
                destAddrTon, destAddrNpi, destinationAddr, dataCoding,
                status, errorCode, optionalParameters
        );

        String expectedString = Converter.valueAsString(dlrRequest);
        assertEquals(expectedString, dlrRequest.toString());
    }

    @Test
    void testMessageRequest_toString() {
        String messageId = "msg123";
        int sourceAddrTon = 1;
        int sourceAddrNpi = 1;
        String sourceAddr = "sourceAddr";
        int destAddrTon = 1;
        int destAddrNpi = 1;
        String destinationAddr = "destAddr";
        Integer registeredDelivery = 1;
        Integer dataCoding = 1;
        String shortMessage = "shortMessage";
        List<UtilsRecords.OptionalParameter> optionalParameters = List.of(new UtilsRecords.OptionalParameter((short) 1, "value"));

        MessageRequest messageRequest = new MessageRequest(
                messageId, sourceAddrTon, sourceAddrNpi, sourceAddr,
                destAddrTon, destAddrNpi, destinationAddr, registeredDelivery,
                dataCoding, shortMessage, optionalParameters
        );

        String expectedString = Converter.valueAsString(messageRequest);
        assertEquals(expectedString, messageRequest.toString());
    }
}
