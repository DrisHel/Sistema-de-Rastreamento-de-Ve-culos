package br.com.rastreamento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingServiceTest {
    @Mock
    private MongoLocationRepository repository;

    @Mock
    private RedisLocationCache cache;

    private TrackingService service;

    @BeforeEach
    void setUp() {
        service = new TrackingService(repository, cache);
    }

    @Test
    void registerPersistsBeforeUpdatingCache() {
        InOrder order = inOrder(repository, cache);
        ArgumentCaptor<VehicleLocation> savedLocation = ArgumentCaptor.forClass(VehicleLocation.class);
        ArgumentCaptor<VehicleLocation> cachedLocation = ArgumentCaptor.forClass(VehicleLocation.class);

        service.register("car-1", -23.55, -46.63);

        order.verify(repository).save(savedLocation.capture());
        order.verify(cache).put(cachedLocation.capture());
        assertEquals(savedLocation.getValue(), cachedLocation.getValue());
        assertEquals("car-1", savedLocation.getValue().vehicleId());
        assertEquals(-23.55, savedLocation.getValue().latitude());
        assertEquals(-46.63, savedLocation.getValue().longitude());
    }

    @Test
    void findLatestReturnsCacheHitWithoutQueryingMongo() {
        VehicleLocation cached = new VehicleLocation("car-1", -23.55, -46.63, Instant.now());
        when(cache.get("car-1")).thenReturn(cached);

        VehicleLocation result = service.findLatest("car-1");

        assertSame(cached, result);
        verify(repository, org.mockito.Mockito.never()).findLatest("car-1");
    }

    @Test
    void findLatestLoadsMongoAndWarmsCacheAfterCacheMiss() {
        VehicleLocation stored = new VehicleLocation("car-1", -23.55, -46.63, Instant.now());
        when(cache.get("car-1")).thenReturn(null);
        when(repository.findLatest("car-1")).thenReturn(stored);

        VehicleLocation result = service.findLatest("car-1");

        assertSame(stored, result);
        verify(cache).put(stored);
    }

    @Test
    void findLatestReturnsNullWhenNeitherCacheNorMongoHasLocation() {
        when(cache.get("car-1")).thenReturn(null);
        when(repository.findLatest("car-1")).thenReturn(null);

        assertNull(service.findLatest("car-1"));
        verify(cache, org.mockito.Mockito.never()).put(any());
    }

    @Test
    void cleanupRefreshesRemainingLocationsAndRemovesEmptyVehicleCache() {
        VehicleLocation remaining = new VehicleLocation("car-1", -23.55, -46.63, Instant.now());
        when(repository.deleteOlderThan(any())).thenReturn(
                new MongoLocationRepository.CleanupResult(3, Set.of("car-1", "car-2")));
        when(repository.findLatest("car-1")).thenReturn(remaining);
        when(repository.findLatest("car-2")).thenReturn(null);
        Instant before = Instant.now();

        long deletedCount = service.cleanHistoryOlderThan(2);

        Instant after = Instant.now();
        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).deleteOlderThan(cutoff.capture());
        assertEquals(3, deletedCount);
        assertTrue(!cutoff.getValue().isBefore(before.minus(2, ChronoUnit.DAYS)));
        assertTrue(!cutoff.getValue().isAfter(after.minus(2, ChronoUnit.DAYS)));
        verify(cache).put(remaining);
        verify(cache).remove("car-2");
    }
}