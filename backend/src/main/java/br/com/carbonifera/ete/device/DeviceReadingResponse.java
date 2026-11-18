package br.com.carbonifera.ete.device;

import java.util.List;

public record DeviceReadingResponse(List<String> accepted, List<FieldResult> rejected, List<FieldResult> discarded) {
    public record FieldResult(String field, String reason) { }
}
