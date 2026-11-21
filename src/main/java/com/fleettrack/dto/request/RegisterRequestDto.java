package com.fleettrack.dto.request;

import com.fleettrack.entity.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequestDto {
    @NotBlank
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
    private String username;

    /** BCrypt only uses the first 72 bytes of a password, so longer ones are rejected rather than silently truncated. */
    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    // Optional; defaults to DISPATCHER. ADMIN requires an admin caller (or no users yet)
    private UserRole role;
}
