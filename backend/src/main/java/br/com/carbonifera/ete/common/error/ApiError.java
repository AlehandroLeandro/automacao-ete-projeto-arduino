package br.com.carbonifera.ete.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<ApiFieldError> fields
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of());
    }

    public static ApiError withFields(int status, String error, String message, String path, List<ApiFieldError> fields) {
        return new ApiError(Instant.now(), status, error, message, path, fields);
    }
}
