package com.adam.restaurantoperations.auth.bootstrap;

import java.util.Map;

import com.adam.restaurantoperations.audit.AuthenticationAuditService;
import com.adam.restaurantoperations.auth.config.AuthProperties;
import com.adam.restaurantoperations.roles.RoleEntity;
import com.adam.restaurantoperations.roles.RoleRepository;
import com.adam.restaurantoperations.users.EmailNormalizer;
import com.adam.restaurantoperations.users.UserEntity;
import com.adam.restaurantoperations.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentAdminBootstrap implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentAdminBootstrap.class);

    private final AuthProperties properties;
    private final EmailNormalizer emailNormalizer;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationAuditService auditService;

    public DevelopmentAdminBootstrap(
            AuthProperties properties,
            EmailNormalizer emailNormalizer,
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationAuditService auditService) {
        this.properties = properties;
        this.emailNormalizer = emailNormalizer;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (isBlank(properties.bootstrapEmail()) || isBlank(properties.bootstrapPassword())) {
            LOGGER.info("Development administrator bootstrap is disabled");
            return;
        }
        String email = emailNormalizer.normalize(properties.bootstrapEmail());
        if (userRepository.existsByEmail(email)) {
            LOGGER.info("Development administrator already exists; bootstrap made no changes");
            return;
        }
        RoleEntity adminRole = roleRepository.findByNameAndEnabledTrue("ADMIN")
                .orElseThrow(() -> new IllegalStateException("The enabled ADMIN role is required for bootstrap"));
        String displayName = isBlank(properties.bootstrapDisplayName())
                ? "Development Administrator"
                : properties.bootstrapDisplayName().strip();
        UserEntity user = userRepository.save(new UserEntity(
                email,
                passwordEncoder.encode(properties.bootstrapPassword()),
                displayName));
        user.assignRole(adminRole);
        userRepository.save(user);
        auditService.record("BOOTSTRAP_ADMIN_CREATED", user.getId(), null, Map.of());
        LOGGER.info("Development administrator created successfully");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
