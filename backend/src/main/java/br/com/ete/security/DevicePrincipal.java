package br.com.ete.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class DevicePrincipal implements UserDetails {

    private final UUID deviceKeyId;
    private final UUID stationId;
    private final String name;

    public DevicePrincipal(UUID deviceKeyId, UUID stationId, String name) {
        this.deviceKeyId = deviceKeyId;
        this.stationId = stationId;
        this.name = name;
    }

    public UUID getDeviceKeyId() {
        return deviceKeyId;
    }

    public UUID getStationId() {
        return stationId;
    }

    public String getName() {
        return name;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_DEVICE"));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return "device:" + deviceKeyId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
