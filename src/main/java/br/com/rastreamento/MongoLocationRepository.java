package br.com.rastreamento;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.result.DeleteResult;
import org.bson.Document;

import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.lt;
import static com.mongodb.client.model.Sorts.descending;

public class MongoLocationRepository implements AutoCloseable {
    private final MongoClient client;
    private final MongoCollection<Document> locations;

    public MongoLocationRepository() {
        String uri = System.getenv().getOrDefault("MONGODB_URI", "mongodb://localhost:27017");
        String databaseName = System.getenv().getOrDefault("MONGODB_DATABASE", "vehicle_tracking");
        client = MongoClients.create(uri);
        MongoDatabase database = client.getDatabase(databaseName);
        locations = database.getCollection("locations");
        locations.createIndex(Indexes.compoundIndex(
                Indexes.ascending("vehicleId"),
                Indexes.descending("recordedAt")
        ));
    }

    public void save(VehicleLocation location) {
        Document document = new Document("vehicleId", location.vehicleId())
                .append("latitude", location.latitude())
                .append("longitude", location.longitude())
                .append("recordedAt", Date.from(location.recordedAt()));
        locations.insertOne(document);
    }

    public VehicleLocation findLatest(String vehicleId) {
        Document document = locations.find(eq("vehicleId", vehicleId))
                .sort(descending("recordedAt"))
                .first();
        if (document == null) {
            return null;
        }

        return new VehicleLocation(
                document.getString("vehicleId"),
                document.getDouble("latitude"),
                document.getDouble("longitude"),
                document.getDate("recordedAt").toInstant()
        );
    }

    public CleanupResult deleteOlderThan(Instant cutoff) {
        Date cutoffDate = Date.from(cutoff);
        Set<String> affectedVehicleIds = new HashSet<>();
        locations.distinct("vehicleId", lt("recordedAt", cutoffDate), String.class)
                .into(affectedVehicleIds);

        DeleteResult result = locations.deleteMany(lt("recordedAt", cutoffDate));
        return new CleanupResult(result.getDeletedCount(), affectedVehicleIds);
    }

    @Override
    public void close() {
        client.close();
    }

    public record CleanupResult(long deletedCount, Set<String> affectedVehicleIds) {
    }
}