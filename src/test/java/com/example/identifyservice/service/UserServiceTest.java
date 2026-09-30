package com.example.identifyservice.service;

import com.example.identifyservice.dto.request.UserCreationRequest;
import com.example.identifyservice.dto.request.UserUpdateRequest;
import com.example.identifyservice.dto.response.UserResponse;
import com.example.identifyservice.dto.response.RoleResponse;
import com.example.identifyservice.exception.AppException;
import com.example.identifyservice.exception.ErrorCode;
import com.example.identifyservice.repository.RoleRepository;
import com.example.identifyservice.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired EntityManager em;

    private UserResponse signUp(String username) {
        return userService.createUser(UserCreationRequest.builder()
                .username(username).password("password123")
                .firstname("A").lastname("B").dob(LocalDate.of(2000, 1, 1)).build());
    }

    @Test
    void newUserGetsUserRoleOnly() {
        var created = signUp("alice");
        assertThat(created.getRoles()).extracting(RoleResponse::getName).containsExactly("USER");
    }

    @Test
    void clientSuppliedIdCannotOverwriteAnotherUser() {
        var victim = signUp("victim");
        var attacker = userService.createUser(UserCreationRequest.builder()
                .id(victim.getId()).username("mallory").password("password123")
                .firstname("M").lastname("M").dob(LocalDate.of(2000, 1, 1)).build());
        em.flush();
        em.clear();

        assertThat(attacker.getId()).isNotEqualTo(victim.getId());
        assertThat(users.findById(victim.getId()).orElseThrow().getUsername()).isEqualTo("victim");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCanEditSelfWithoutPasswordAndCannotGrantAdmin() {
        var alice = signUp("alice");
        String hashBefore = users.findById(alice.getId()).orElseThrow().getPassword();
        String adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();

        userService.updateUser(alice.getId(),
                UserUpdateRequest.builder().firstname("Alicia").roles(List.of(adminRoleId)).build());
        em.flush();
        em.clear();

        var reloaded = users.findById(alice.getId()).orElseThrow();
        assertThat(reloaded.getFirstname()).isEqualTo("Alicia");
        assertThat(reloaded.getPassword()).isEqualTo(hashBefore);
        assertThat(reloaded.getDob()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(reloaded.getRoles()).extracting(r -> r.getName()).containsExactly("USER");
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void userCannotEditSomeoneElse() {
        signUp("alice");
        var bob = signUp("bob");
        assertThatThrownBy(() -> userService.updateUser(bob.getId(),
                UserUpdateRequest.builder().firstname("Hacked").build()))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanChangeRoles() {
        var alice = signUp("alice");
        String adminRoleId = roles.findByName("ADMIN").orElseThrow().getId();
        userService.updateUser(alice.getId(), UserUpdateRequest.builder().roles(List.of(adminRoleId)).build());
        em.flush();
        em.clear();
        assertThat(users.findById(alice.getId()).orElseThrow().getRoles())
                .extracting(r -> r.getName()).containsExactly("ADMIN");
    }
}
