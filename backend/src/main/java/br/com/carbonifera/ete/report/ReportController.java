package br.com.carbonifera.ete.report;

import br.com.carbonifera.ete.security.UserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
public class ReportController {

    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/{type}")
    public ReportResponse report(
            @PathVariable ReportType type,
            @RequestParam Instant startAt,
            @RequestParam Instant endAt,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.generate(type, startAt, endAt, stationId, consolidated, principal);
    }

    @GetMapping(value = "/{type}/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportCsv(
            @PathVariable ReportType type,
            @RequestParam Instant startAt,
            @RequestParam Instant endAt,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportResponse report = service.generate(type, startAt, endAt, stationId, consolidated, principal);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(type.name().toLowerCase() + ".csv").build());
        return ResponseEntity.ok().headers(headers).body(service.toCsv(report));
    }

    @GetMapping("/{type}/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(
            @PathVariable ReportType type,
            @RequestParam Instant startAt,
            @RequestParam Instant endAt,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        ReportResponse report = service.generate(type, startAt, endAt, stationId, consolidated, principal);
        byte[] content;
        try {
            content = service.toXlsx(report);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, exception.getMessage(), exception);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(type.name().toLowerCase() + ".xlsx").build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @GetMapping("/{type}/export.pdf")
    public ResponseEntity<Void> exportPdf(
            @PathVariable ReportType type,
            @RequestParam Instant startAt,
            @RequestParam Instant endAt,
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        service.generate(type, startAt, endAt, stationId, consolidated, principal);
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "PDF export is not implemented yet");
    }
}
