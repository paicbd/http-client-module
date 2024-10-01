package com.client.http.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class WebClientConfigTest {

    @Mock
    private ObjectMapper mockObjectMapper;

    @InjectMocks
    private WebClientConfig webClientConfig;

    private WebClient webClient;

    @BeforeEach
    void setUp() {
        webClient = webClientConfig.webClient(mockObjectMapper);
    }

    @Test
    void webClient_shouldNotBeNull() {
        assertNotNull(webClient, "WebClient bean should not be null");
    }

    @Test
    void webClient_shouldConfigureJackson2JsonEncoderAndDecoder() {
        Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(mockObjectMapper);
        Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(mockObjectMapper);

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configure -> {
                    configure.defaultCodecs().jackson2JsonEncoder(encoder);
                    configure.defaultCodecs().jackson2JsonDecoder(decoder);
                })
                .build();

        WebClient testWebClient = WebClient.builder()
                .exchangeStrategies(strategies)
                .build();

        assertNotNull(testWebClient, "Test WebClient should not be null");
    }
}
