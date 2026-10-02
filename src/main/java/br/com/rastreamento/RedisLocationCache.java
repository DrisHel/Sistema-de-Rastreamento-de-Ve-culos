package br.com.rastreamento;

import redis.clients.jedis.JedisPooled;

import java.net.URI;
import java.time.Instant;

public class RedisLocationCache implements AutoCloseable {
    private static final long TTL_SECONDS = 5 * 60;
    private static final String KEY_PREFIX = "vehicle:location:";

    private final JedisPooled redis;

    public RedisLocationCache() {
        this(new JedisPooled(URI.create(
            System.getenv().getOrDefault("REDIS_URL", "redis://localhost:6379"))));
    }

    RedisLocationCache(JedisPooled redis) {
        this.redis = redis;
    }

    public VehicleLocation get(String vehicleId) {
        String value = redis.get(key(vehicleId));
        if (value == null) {
            return null;
        }

        String[] parts = value.split("\\|", -1);
        return new VehicleLocation(
                vehicleId,
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Instant.parse(parts[2])
        );
    }

    public void put(VehicleLocation location) {
        String value = location.latitude() + "|" + location.longitude() + "|" + location.recordedAt();
        redis.setex(key(location.vehicleId()), TTL_SECONDS, value);
    }

    public void remove(String vehicleId) {
        redis.del(key(vehicleId));
    }

    private String key(String vehicleId) {
        return KEY_PREFIX + vehicleId;
    }

    @Override
    public void close() {
        redis.close();
    }
}