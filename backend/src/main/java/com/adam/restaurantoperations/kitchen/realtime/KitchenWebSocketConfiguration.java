package com.adam.restaurantoperations.kitchen.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class KitchenWebSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final KitchenStompChannelInterceptor interceptor;
    private final SafeStompErrorHandler errorHandler;
    private final String frontendOrigin;

    public KitchenWebSocketConfiguration(
            KitchenStompChannelInterceptor interceptor,
            SafeStompErrorHandler errorHandler,
            @Value("${app.frontend-origin}") String frontendOrigin) {
        this.interceptor = interceptor;
        this.errorHandler = errorHandler;
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setErrorHandler(errorHandler);
        registry.addEndpoint("/ws").setAllowedOrigins(frontendOrigin);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(interceptor);
    }
}
