package com.adam.restaurantoperations.kitchen.realtime;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Component;

@Component
public class KitchenStompChannelInterceptor implements ChannelInterceptor {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter authenticationConverter;

    public KitchenStompChannelInterceptor(
            JwtDecoder decoder,
            JwtAuthenticationConverter authenticationConverter) {
        this.decoder = decoder;
        this.authenticationConverter = authenticationConverter;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            accessor = StompHeaderAccessor.wrap(message);
        }
        StompCommand command = accessor.getCommand();
        if (command == null) {
            return message;
        }
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION)));
        } else if (command == StompCommand.SUBSCRIBE) {
            requireAdmin(accessor.getUser());
            if (!Objects.equals(KitchenRealtimeBroadcaster.KITCHEN_TOPIC, accessor.getDestination())) {
                throw new AccessDeniedException("WebSocket subscription is not allowed");
            }
        } else if (command == StompCommand.SEND) {
            throw new AccessDeniedException("WebSocket client commands are not allowed");
        }
        return message;
    }

    private Authentication authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("WebSocket authentication is required");
        }
        try {
            Jwt jwt = decoder.decode(authorization.substring(BEARER_PREFIX.length()));
            Authentication authentication = authenticationConverter.convert(jwt);
            requireAdmin(authentication);
            return authentication;
        } catch (JwtException exception) {
            throw new AccessDeniedException("WebSocket authentication failed");
        }
    }

    private void requireAdmin(java.security.Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !authentication.isAuthenticated()
                || authentication.getAuthorities().stream()
                        .noneMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            throw new AccessDeniedException("ADMIN authority is required");
        }
    }
}
