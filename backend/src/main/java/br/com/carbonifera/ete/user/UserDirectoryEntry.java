package br.com.carbonifera.ete.user;

import java.util.UUID;

public record UserDirectoryEntry(UUID id, String name, UserRole role) {

    public static UserDirectoryEntry from(AppUser user) {
        return new UserDirectoryEntry(user.getId(), user.getName(), user.getRole());
    }
}
