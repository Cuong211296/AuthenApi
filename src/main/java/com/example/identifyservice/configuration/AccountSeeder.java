package com.example.identifyservice.configuration;

import com.example.identifyservice.entity.Role;
import com.example.identifyservice.entity.User;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AccountSeeder {
    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    ShopProperties shopProperties;

    @Transactional
    public void seed() {
        Role adminRole = ensureRole("ADMIN", "Administrator");
        ensureRole("USER", "Customer");

        User admin = userRepository.findByUsername("admin").orElseGet(() -> {
            log.warn("admin user created; its password is taken from ADMIN_PASSWORD");
            return User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode(shopProperties.adminPassword()))
                    .dob(LocalDate.of(1990, 1, 1))
                    .roles(new HashSet<>())
                    .build();
        });
        if (admin.getRoles() == null) admin.setRoles(new HashSet<>());
        if (admin.getRoles().stream().noneMatch(r -> "ADMIN".equals(r.getName()))) {
            admin.getRoles().add(adminRole);
        }
        userRepository.save(admin);
    }

    private Role ensureRole(String name, String description) {
        return roleRepository.findByName(name).orElseGet(() ->
                roleRepository.save(Role.builder().name(name).description(description).build()));
    }
}
