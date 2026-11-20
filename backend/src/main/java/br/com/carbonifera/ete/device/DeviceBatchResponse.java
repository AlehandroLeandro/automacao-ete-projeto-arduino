package br.com.carbonifera.ete.device;

import java.util.List;

public record DeviceBatchResponse(List<Result> results) {
    public record Result(String id, List<String> accepted, List<DeviceReadingResponse.FieldResult> rejected,
                         List<DeviceReadingResponse.FieldResult> discarded, boolean duplicate) { }
}
