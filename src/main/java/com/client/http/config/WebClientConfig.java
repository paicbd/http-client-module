package com.client.http.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(ObjectMapper mapper) {
        final Jackson2JsonEncoder encoder = new Jackson2JsonEncoder(mapper);
        final Jackson2JsonDecoder decoder = new Jackson2JsonDecoder(mapper);

        final ExchangeStrategies strategies = ExchangeStrategies
                .builder()
                .codecs(configure -> {
                    configure.defaultCodecs().jackson2JsonEncoder(encoder);
                    configure.defaultCodecs().jackson2JsonDecoder(decoder);
                }).build();

        return WebClient.builder()
                .exchangeStrategies(strategies)
                .build();
    }
}
