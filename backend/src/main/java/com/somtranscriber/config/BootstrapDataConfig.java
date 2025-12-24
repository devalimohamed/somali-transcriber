package com.somtranscriber.config;

import com.somtranscriber.auth.model.UserEntity;
import com.somtranscriber.auth.model.UserRole;
import com.somtranscriber.auth.model.UserStatus;
import com.somtranscriber.auth.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BootstrapDataConfig {

    private static final Logger log = LoggerFactory.getLogger(BootstrapDataConfig.class);

    @Bean
    CommandLineRunner bootstrapOperator(UserRepository userRepository,
                                        PasswordEncoder passwordEncoder,
                                        @Value("${BOOTSTRAP_USERNAME:${BOOTSTRAP_OPERATOR_EMAIL:}}") String bootstrapUsername,
                                        @Value("${BOOTSTRAP_PIN:${BOOTSTRAP_OPERATOR_PASSWORD:}}") String bootstrapPin) {
        return args -> {
            if (bootstrapUsername.isBlank() || bootstrapPin.isBlank()) {
                return;
            }

            userRepository.findByEmail(bootstrapUsername.trim().toLowerCase()).ifPresentOrElse(
                    existing -> log.info("Bootstrap operator already exists: {}", existing.getEmail()),
                    () -> {
                        UserEntity operator = new UserEntity();
                        operator.setEmail(bootstrapUsername);
                        operator.setPasswordHash(passwordEncoder.encode(bootstrapPin));
                        operator.setRole(UserRole.OPERATOR);
                        operator.setStatus(UserStatus.ACTIVE);
                        userRepository.save(operator);
                        log.info("Created bootstrap operator: {}", bootstrapUsername);
                    }
            );
        };
    }
}
