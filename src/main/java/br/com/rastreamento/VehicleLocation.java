package br.com.rastreamento;

import java.time.Instant;

public record VehicleLocation(
        String vehicleId,
        double latitude,
        double longitude,
        Instant recordedAt
) {
}