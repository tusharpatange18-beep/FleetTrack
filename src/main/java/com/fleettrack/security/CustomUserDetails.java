package com.fleettrack.security;

import com.fleettrack.entity.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Wraps our AppUser so Spring Security can read it. Spring Security only knows the
 * UserDetails interface, so we answer its questions (username, password, roles) here.
 */
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final AppUser appUser;

    /** Each user has exactly one role, e.g. ADMIN or DISPATCHER. */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority(appUser.getRole().name()));
    }

    @Override
    public String getPassword() {
        return appUser.getPassword();
    }

    @Override
    public String getUsername() {
        return appUser.getUsername();
    }

    // We don't use account expiry, locking or disabling, so all four checks always pass
    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
