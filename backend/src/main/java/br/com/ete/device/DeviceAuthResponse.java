package br.com.ete.device;

public record DeviceAuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
