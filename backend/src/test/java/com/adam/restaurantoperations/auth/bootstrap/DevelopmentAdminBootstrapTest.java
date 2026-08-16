package com.adam.restaurantoperations.auth.bootstrap;

import java.time.Duration;
import java.util.Optional;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.roles.RoleEntity;
import com.adam.restaurantoperations.roles.RoleRepository;
import com.adam.restaurantoperations.users.EmailNormalizer;
import com.adam.restaurantoperations.users.UserEntity;
import com.adam.restaurantoperations.users.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DevelopmentAdminBootstrapTest {

    @Test
    void createsAdministratorOnceWhenAbsent() {
        Fixture fixture = new Fixture(properties("admin@example.com", "bootstrap-password"));
        RoleEntity adminRole = mock(RoleEntity.class);
        given(fixture.emailNormalizer.normalize("admin@example.com")).willReturn("admin@example.com");
        given(fixture.userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(fixture.roleRepository.findByNameAndEnabledTrue("ADMIN")).willReturn(Optional.of(adminRole));
        given(fixture.passwordEncoder.encode("bootstrap-password")).willReturn("encoded-password");
        given(fixture.userRepository.save(any(UserEntity.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        fixture.bootstrap.run(mock(ApplicationArguments.class));

        verify(fixture.passwordEncoder).encode("bootstrap-password");
        verify(fixture.userRepository, times(2)).save(any(UserEntity.class));
        verify(fixture.auditService).record(
                "BOOTSTRAP_ADMIN_CREATED",
                null,
                null,
                java.util.Map.of());
    }

    @Test
    void existingAdministratorIsNeverOverwrittenOrDuplicated() {
        Fixture fixture = new Fixture(properties("admin@example.com", "new-password"));
        given(fixture.emailNormalizer.normalize("admin@example.com")).willReturn("admin@example.com");
        given(fixture.userRepository.existsByEmail("admin@example.com")).willReturn(true);

        fixture.bootstrap.run(mock(ApplicationArguments.class));

        verify(fixture.passwordEncoder, never()).encode(any());
        verify(fixture.roleRepository, never()).findByNameAndEnabledTrue(any());
        verify(fixture.userRepository, never()).save(any());
        verify(fixture.auditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void absentConfigurationDisablesBootstrapWithoutPersistingAnything() {
        Fixture fixture = new Fixture(properties("", ""));

        fixture.bootstrap.run(mock(ApplicationArguments.class));

        verify(fixture.emailNormalizer, never()).normalize(any());
        verify(fixture.userRepository, never()).save(any());
        verify(fixture.passwordEncoder, never()).encode(any());
    }

    @Test
    void missingEnabledAdminRoleFailsBeforeCreatingUser() {
        Fixture fixture = new Fixture(properties("admin@example.com", "bootstrap-password"));
        given(fixture.emailNormalizer.normalize("admin@example.com")).willReturn("admin@example.com");
        given(fixture.userRepository.existsByEmail("admin@example.com")).willReturn(false);
        given(fixture.roleRepository.findByNameAndEnabledTrue("ADMIN")).willReturn(Optional.empty());

        assertThatThrownBy(() -> fixture.bootstrap.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("The enabled ADMIN role is required for bootstrap");

        verify(fixture.userRepository, never()).save(any());
        verify(fixture.passwordEncoder, never()).encode(any());
    }

    @Test
    void bootstrapBeanIsAbsentFromDefaultTestAndProductionProfiles() {
        var contextRunner = new ApplicationContextRunner().withUserConfiguration(BootstrapOnlyConfiguration.class);

        contextRunner.run(context -> assertThat(context).doesNotHaveBean(DevelopmentAdminBootstrap.class));
        contextRunner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentAdminBootstrap.class));
        contextRunner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(DevelopmentAdminBootstrap.class));
    }

    private AuthProperties properties(String email, String password) {
        return new AuthProperties(
                "bootstrap-test-jwt-secret-with-at-least-32-bytes",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                false,
                "Lax",
                email,
                password,
                "Development Administrator");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(DevelopmentAdminBootstrap.class)
    static class BootstrapOnlyConfiguration {
    }

    private static final class Fixture {

        private final EmailNormalizer emailNormalizer = mock(EmailNormalizer.class);
        private final UserRepository userRepository = mock(UserRepository.class);
        private final RoleRepository roleRepository = mock(RoleRepository.class);
        private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        private final AuthenticationAuditService auditService = mock(AuthenticationAuditService.class);
        private final DevelopmentAdminBootstrap bootstrap;

        private Fixture(AuthProperties properties) {
            bootstrap = new DevelopmentAdminBootstrap(
                    properties,
                    emailNormalizer,
                    userRepository,
                    roleRepository,
                    passwordEncoder,
                    auditService);
        }
    }
}
