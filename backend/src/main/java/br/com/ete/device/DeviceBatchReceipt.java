package br.com.ete.device;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "device_batch_receipts", uniqueConstraints = @UniqueConstraint(columnNames = {"device_key_id", "sample_id"}))
class DeviceBatchReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "device_key_id", nullable = false)
    private UUID deviceKeyId;
    @Column(name = "sample_id", nullable = false, length = 64)
    private String sampleId;
    @Column(nullable = false, length = 8192)
    private String result;
    protected DeviceBatchReceipt() { }
    DeviceBatchReceipt(UUID keyId, String sampleId, String result) {
        this.deviceKeyId = keyId; this.sampleId = sampleId; this.result = result;
    }
    String result() { return result; }
}
