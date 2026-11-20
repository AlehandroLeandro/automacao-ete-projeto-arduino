package br.com.carbonifera.ete.device;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DeviceBatchReceiptRepository extends JpaRepository<DeviceBatchReceipt, UUID> {
    Optional<DeviceBatchReceipt> findByDeviceKeyIdAndSampleId(UUID deviceKeyId, String sampleId);
}
