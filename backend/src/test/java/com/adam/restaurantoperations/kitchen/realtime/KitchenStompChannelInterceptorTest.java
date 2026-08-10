package com.adam.restaurantoperations.kitchen.realtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class KitchenStompChannelInterceptorTest {
    private JwtDecoder decoder;
    private KitchenStompChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        decoder = mock(JwtDecoder.class);
        var authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        interceptor = new KitchenStompChannelInterceptor(decoder, converter);
    }

    @Test
    void connectRequiresValidAdminBearerTokenWithoutEchoingIt() {
        given(decoder.decode("valid-token")).willReturn(jwt(List.of("ADMIN")));
        Message<?> connected = interceptor.preSend(
                message(StompCommand.CONNECT, null, "Bearer valid-token", null),
                ignoredChannel());
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(connected);
        assertThat(accessor.getUser()).isNotNull();
        assertThat(accessor.getUser().getName()).isEqualTo("7");

        assertThatThrownBy(() -> interceptor.preSend(
                        message(StompCommand.CONNECT, null, null, null),
                        ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket authentication is required");

        given(decoder.decode("expired-token")).willThrow(new BadJwtException("expired details"));
        assertThatThrownBy(() -> interceptor.preSend(
                        message(StompCommand.CONNECT, null, "Bearer expired-token", null),
                        ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket authentication failed")
                .hasMessageNotContaining("expired-token")
                .hasMessageNotContaining("expired details");
    }

    @Test
    void nonAdminConnectSubscriptionAndClientSendAreRejected() {
        given(decoder.decode("server-token")).willReturn(jwt(List.of("SERVER")));
        assertThatThrownBy(() -> interceptor.preSend(
                        message(StompCommand.CONNECT, null, "Bearer server-token", null),
                        ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class);

        var admin = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "7",
                "unused",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        interceptor.preSend(
                message(StompCommand.SUBSCRIBE, KitchenRealtimeBroadcaster.KITCHEN_TOPIC, null, admin),
                ignoredChannel());
        assertThatThrownBy(() -> interceptor.preSend(
                        message(StompCommand.SUBSCRIBE, "/topic/private", null, admin),
                        ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(
                        message(StompCommand.SEND, "/topic/kitchen", null, admin),
                        ignoredChannel()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("WebSocket client commands are not allowed");
    }

    private Jwt jwt(List<String> roles) {
        return new Jwt(
                "decoded-value",
                Instant.now().minusSeconds(5),
                Instant.now().plusSeconds(300),
                Map.of("alg", "HS256"),
                Map.of("sub", "7", "roles", roles));
    }

    private Message<byte[]> message(
            StompCommand command,
            String destination,
            String authorization,
            java.security.Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (user != null) {
            accessor.setUser(user);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private org.springframework.messaging.MessageChannel ignoredChannel() {
        return mock(org.springframework.messaging.MessageChannel.class);
    }
}
