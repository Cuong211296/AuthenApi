package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.Role;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountSeederTest {
    @Autowired AccountSeeder seeder;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;

    @Test
    void adminExistsWithAdminRoleAndConfiguredPassword() {
        var admin = users.findByUsername("admin").orElseThrow();
        assertThat(admin.getRoles()).extracting(Role::getName).contains("ADMIN");
        assertThat(encoder.matches("Admin#12345", admin.getPassword())).isTrue();
        assertThat(roles.findByName("USER")).isPresent();
    }

    @Test
    void seedRepairsAdminThatLostItsRole() {
        var admin = users.findByUsername("admin").orElseThrow();
        admin.getRoles().clear();
        users.save(admin);
        em.flush();

        seeder.seed();
        em.flush();
        em.clear();

        assertThat(users.findByUsername("admin").orElseThrow().getRoles())
                .extracting(Role::getName).contains("ADMIN");
    }

    @Test
    void seedIsIdempotent() {
        seeder.seed();
        seeder.seed();
        assertThat(roles.findAll().stream().filter(r -> r.getName().equals("ADMIN")).count()).isEqualTo(1);
    }
}
