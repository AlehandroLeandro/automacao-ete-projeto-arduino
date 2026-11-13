package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class UserPrincipal implements UserDetails {

    private final UUID id;
    private final String name;
    private final String username;
    private final String password;
    private final UserRole role;
    private final UUID stationId;
    private final String stationName;
    private final boolean changePassword;
    private final boolean active;

    private UserPrincipal(AppUser user) {
        this.id = user.getId();
        this.name = user.getName();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.role = user.getRole();
        this.stationId = user.getStation() == null ? null : user.getStation().getId();
        this.stationName = user.getStation() == null ? null : user.getStation().getName();
        this.changePassword = user.isChangePassword();
        this.active = user.isActive();
    }

    public static UserPrincipal from(AppUser user) {
        return new UserPrincipal(user);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public UUID getStationId() {
        return stationId;
    }

    public String getStationName() {
        return stationName;
    }

    public boolean isChangePassword() {
        return changePassword;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
