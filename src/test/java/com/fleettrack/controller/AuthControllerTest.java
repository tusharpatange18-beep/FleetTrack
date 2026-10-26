package com.fleettrack.controller;

import com.fleettrack.dto.request.RegisterRequestDto;
import com.fleettrack.entity.AppUser;
import com.fleettrack.entity.enums.UserRole;
import com.fleettrack.repository.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerTest {

    @Mock
    private AppUserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(userRepository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_defaultsToDispatcherWhenRoleMissing() {
        when(userRepository.count()).thenReturn(3L);

        authController.register(request(null));

        assertEquals(UserRole.DISPATCHER, savedUser().getRole());
    }

    @Test
    void register_rejectsAdminFromAnonymousCaller() {
        when(userRepository.count()).thenReturn(3L);

        assertThrows(AccessDeniedException.class, () -> authController.register(request(UserRole.ADMIN)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsAdminFromDispatcherCaller() {
        when(userRepository.count()).thenReturn(3L);
        authenticateAs(UserRole.DISPATCHER);

        assertThrows(AccessDeniedException.class, () -> authController.register(request(UserRole.ADMIN)));
    }

    @Test
    void register_allowsAdminFromAdminCaller() {
        when(userRepository.count()).thenReturn(3L);
        authenticateAs(UserRole.ADMIN);

        authController.register(request(UserRole.ADMIN));

        assertEquals(UserRole.ADMIN, savedUser().getRole());
    }

    @Test
    void register_allowsFirstUserToBeAdmin() {
        when(userRepository.count()).thenReturn(0L);

        authController.register(request(UserRole.ADMIN));

        assertEquals(UserRole.ADMIN, savedUser().getRole());
    }

    private RegisterRequestDto request(UserRole role) {
        RegisterRequestDto dto = new RegisterRequestDto();
        dto.setUsername("user1");
        dto.setPassword("secret");
        dto.setRole(role);
        return dto;
    }

    private void authenticateAs(UserRole role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "caller", null, List.of(new SimpleGrantedAuthority(role.name()))));
    }

    private AppUser savedUser() {
        ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }
}
