package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offline-queue/failures")
public class OfflineFailureController {
    private final NotificationDispatcher dispatcher;
    private final NotificationRecipientResolver recipients;
    private final StationRepository stations;

    public OfflineFailureController(NotificationDispatcher dispatcher, NotificationRecipientResolver recipients,
                                    StationRepository stations) {
        this.dispatcher = dispatcher;
        this.recipients = recipients;
        this.stations = stations;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public void report(@Valid @RequestBody OfflineFailureRequest request) {
        Station station = request.stationId() == null ? null : stations.findById(request.stationId()).orElse(null);
        dispatcher.dispatch(new NotificationCommand(
                NotificationType.OFFLINE_SYNC_FAILURE,
                recipients.activeSupervisorsAndAdmins(),
                station,
                "Falha na sincronização offline",
                request.domain() + " / " + request.operation() + ": " + request.reason(),
                "/app/pendencias",
                request.clientRequestId().toString(),
                null
        ));
    }
}
