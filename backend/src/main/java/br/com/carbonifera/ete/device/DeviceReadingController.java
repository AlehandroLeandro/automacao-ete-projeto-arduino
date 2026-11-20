package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.security.DevicePrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device/readings")
public class DeviceReadingController {
    private final DeviceReadingService service;
    private final DeviceBatchService batches;
    public DeviceReadingController(DeviceReadingService service, DeviceBatchService batches) { this.service = service; this.batches = batches; }

    @PostMapping
    public DeviceReadingResponse receive(@AuthenticationPrincipal DevicePrincipal principal,
                                         @RequestBody DeviceReadingRequest request) {
        return service.receive(principal.getDeviceKeyId(), request);
    }
    @PostMapping("/batch")
    public DeviceBatchResponse receiveBatch(@AuthenticationPrincipal DevicePrincipal principal,
                                            @RequestBody DeviceBatchRequest request) throws com.fasterxml.jackson.core.JsonProcessingException {
        return batches.receive(principal.getDeviceKeyId(), request);
    }
}
