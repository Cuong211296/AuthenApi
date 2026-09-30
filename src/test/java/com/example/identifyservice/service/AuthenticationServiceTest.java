package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.AuthenticationRequest;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthenticationServiceTest {
    @Autowired AuthenticationService authService;
    @Autowired UserRepository users;

    User carol;

    @BeforeEach
    void setUp() {
        carol = users.save(User.builder().username("carol")
                .password(new BCryptPasswordEncoder(10).encode("password123"))
                .dob(LocalDate.of(2000, 1, 1)).build());
    }

    private AuthenticationRequest req(String u, String p) {
        return AuthenticationRequest.builder().username(u).password(p).build();
    }

    private void assertUnauthenticated(Runnable call) {
        assertThatThrownBy(call::run).isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void unknownUserAndWrongPasswordLookTheSame() {
        assertUnauthenticated(() -> authService.authenticate(req("nobody", "password123")));
        assertUnauthenticated(() -> authService.authenticate(req("carol", "wrong-password")));
    }

    @Test
    void successfulLoginRecordsLastLoginAndResetsAttempts() {
        assertUnauthenticated(() -> authService.authenticate(req("carol", "bad")));
        var response = authService.authenticate(req("carol", "password123"));
        assertThat(response.isAuthenticated()).isTrue();
        assertThat(response.getToken()).isNotBlank();
        assertThat(carol.getLastLoginAt()).isNotNull();
        assertThat(carol.getLoginAttempts()).isZero();
    }

    @Test
    void suspendedUserCannotLogin() {
        carol.setStatus("SUSPENDED");
        users.save(carol);
        assertUnauthenticated(() -> authService.authenticate(req("carol", "password123")));
    }

    @Test
    void fiveFailuresLockTheAccountEvenForTheCorrectPassword() {
        for (int i = 0; i < 5; i++) {
            assertUnauthenticated(() -> authService.authenticate(req("carol", "bad")));
        }
        assertThat(carol.getLockedUntil()).isAfter(Instant.now());
        assertUnauthenticated(() -> authService.authenticate(req("carol", "password123")));
    }

    @Test
    void expiredLockNoLongerBlocksLogin() {
        carol.setLockedUntil(Instant.now().minus(1, ChronoUnit.MINUTES));
        users.save(carol);
        assertThat(authService.authenticate(req("carol", "password123")).isAuthenticated()).isTrue();
    }
}
