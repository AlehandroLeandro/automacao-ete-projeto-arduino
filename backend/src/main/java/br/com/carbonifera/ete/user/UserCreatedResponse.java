package br.com.carbonifera.ete.user;

public record UserCreatedResponse(
        UserResponse user,
        String temporaryPassword
) {
}
