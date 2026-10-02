package br.com.rastreamento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import redis.clients.jedis.JedisPooled;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisLocationCacheTest {
    @Mock
    private JedisPooled redis;

    private RedisLocationCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisLocationCache(redis);
    }

    @Test
    void putStoresLocationWithFiveMinuteTtl() {
        Instant recordedAt = Instant.parse("2026-10-02T12:00:00Z");
        VehicleLocation location = new VehicleLocation("car-1", -23.55, -46.63, recordedAt);

        cache.put(location);

        verify(redis).setex(
                "vehicle:location:car-1",
                300,
                "-23.55|-46.63|2026-10-02T12:00:00Z"
        );
    }

    @Test
    void getReconstructsCachedLocation() {
        Instant recordedAt = Instant.parse("2026-10-02T12:00:00Z");
        when(redis.get("vehicle:location:car-1"))
                .thenReturn("-23.55|-46.63|2026-10-02T12:00:00Z");

        VehicleLocation result = cache.get("car-1");

        assertEquals(new VehicleLocation("car-1", -23.55, -46.63, recordedAt), result);
    }

    @Test
    void removeDeletesVehicleKey() {
        cache.remove("car-1");

        verify(redis).del("vehicle:location:car-1");
    }
}