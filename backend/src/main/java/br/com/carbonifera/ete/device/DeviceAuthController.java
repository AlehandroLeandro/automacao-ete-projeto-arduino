package br.com.carbonifera.ete.device;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeviceAuthController {
    private final DeviceAuthService service;
    private final ObjectMapper objectMapper;

    public DeviceAuthController(DeviceAuthService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/auth/device-login")
    public DeviceAuthResponse login(@RequestBody(required = false) String body, HttpServletRequest request) {
        return service.login(extractKey(body), request.getRemoteAddr());
    }

    private String extractKey(String body) {
        if (body == null) return null;
        try {
            JsonNode json = objectMapper.readTree(body);
            JsonNode key = json == null ? null : json.get("key");
            return key != null && key.isTextual() ? key.textValue() : null;
        } catch (java.io.IOException exception) {
            return null;
        }
    }
}
