package br.com.rastreamento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class TrackingService {
    private final MongoLocationRepository repository;
    private final RedisLocationCache cache;

    public TrackingService(MongoLocationRepository repository, RedisLocationCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public void register(String vehicleId, double latitude, double longitude) {
        VehicleLocation location = new VehicleLocation(vehicleId, latitude, longitude, Instant.now());
        repository.save(location);
        cache.put(location);
    }

    public VehicleLocation findLatest(String vehicleId) {
        VehicleLocation location = cache.get(vehicleId);
        if (location == null) {
            location = repository.findLatest(vehicleId);
            if (location != null) {
                cache.put(location);
            }
        }
        return location;
    }

    public long cleanHistoryOlderThan(long days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        MongoLocationRepository.CleanupResult result = repository.deleteOlderThan(cutoff);

        for (String vehicleId : result.affectedVehicleIds()) {
            VehicleLocation latest = repository.findLatest(vehicleId);
            if (latest == null) {
                cache.remove(vehicleId);
            } else {
                cache.put(latest);
            }
        }
        return result.deletedCount();
    }
}