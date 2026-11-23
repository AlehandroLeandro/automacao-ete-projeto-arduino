package br.com.ete.user;

public record UserCreatedResponse(
        UserResponse user,
        String temporaryPassword
) {
}
