package com.backend.iniato.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint the mobile app connects to
        registry.addEndpoint("/ws/driver-location")
                .setAllowedOriginPatterns("*") // adjust for production
                .withSockJS(); // fallback for browsers
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Prefix for messages coming from client (to server)
        config.setApplicationDestinationPrefixes("/app");
        // Prefix for messages being broadcast (from server to clients)
        config.enableSimpleBroker("/topic");
    }
}