package br.com.carbonifera.ete.device;

public record DeviceAuthResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
