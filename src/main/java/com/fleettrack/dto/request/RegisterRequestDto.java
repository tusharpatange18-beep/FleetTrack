package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RegisterRequestDto {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    // Optional; defaults to DISPATCHER. ADMIN requires an admin caller (or no users yet)
    private UserRole role;
}
