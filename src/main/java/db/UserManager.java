package db;

import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import org.bson.Document;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.geojson.Point;
import com.mongodb.client.model.geojson.Position;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

public class UserManager {

    // --- Migration updated: auto-fix swapped values & drop invalid ---
    public static void migrateStringLocationsToGeoJSON() {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        int fixed = 0, removed = 0, alreadyOk = 0;
        try (MongoCursor<Document> cursor = users.find(Filters.type("location", "string")).iterator()) {
            while (cursor.hasNext()) {
                Document user = cursor.next();
                String raw = user.getString("location");
                if (raw == null || raw.trim().isEmpty()) continue;
                String[] parts = raw.split(",");
                org.bson.types.ObjectId id = user.getObjectId("_id");
                if (parts.length != 2) {
                    users.updateOne(Filters.eq("_id", id), new Document("$unset", new Document("location", "")));
                    removed++; continue;
                }
                try {
                    double first = Double.parseDouble(parts[0].trim());
                    double second = Double.parseDouble(parts[1].trim());
                    Double lat = null, lon = null;
                    // Assume stored as lat,lon originally
                    if (inLat(first) && inLon(second)) { lat = first; lon = second; }
                    // If reversed
                    else if (inLon(first) && inLat(second)) { lon = first; lat = second; }
                    // If first invalid but second looks like lat (swap attempt)
                    else if (!inLat(first) && inLat(second) && inLon(first)) { lon = first; lat = second; }
                    if (lat == null || lon == null) {
                        users.updateOne(Filters.eq("_id", id), new Document("$unset", new Document("location", "")));
                        removed++; continue;
                    }
                    Document geo = new Document("type", "Point").append("coordinates", Arrays.asList(lon, lat));
                    users.updateOne(Filters.eq("_id", id), new Document("$set", new Document("location", geo)));
                    fixed++;
                } catch (NumberFormatException ex) {
                    users.updateOne(Filters.eq("_id", id), new Document("$unset", new Document("location", "")));
                    removed++;
                }
            }
        }
        // Clean any existing bad geo docs (lat > 90 etc.)
        try (MongoCursor<Document> cursor = users.find(Filters.type("location", "object")).iterator()) {
            while (cursor.hasNext()) {
                Document user = cursor.next();
                Document loc = (Document) user.get("location");
                if (loc == null) continue;
                @SuppressWarnings("unchecked")
                List<Double> coords = (List<Double>) loc.get("coordinates");
                if (coords == null || coords.size() != 2) continue;
                double lon = coords.get(0); double lat = coords.get(1);
                if (!inLat(lat) || !inLon(lon)) {
                    // Try swap
                    if (inLat(lon) && inLon(lat)) {
                        org.bson.types.ObjectId id = user.getObjectId("_id");
                        Document geo = new Document("type", "Point").append("coordinates", Arrays.asList(lat, lon));
                        users.updateOne(Filters.eq("_id", id), new Document("$set", new Document("location", geo)));
                        fixed++;
                    } else {
                        users.updateOne(Filters.eq("_id", user.getObjectId("_id")), new Document("$unset", new Document("location", "")));
                        removed++;
                    }
                } else {
                    alreadyOk++;
                }
            }
        }
        System.out.println("Location data migration complete. fixed=" + fixed + " removed=" + removed + " ok=" + alreadyOk);
    }

    private static boolean inLat(double v) { return v >= -90 && v <= 90; }
    private static boolean inLon(double v) { return v >= -180 && v <= 180; }

    public static void ensureLocationIndex() {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        // Remove any invalid geo docs (lat > 90 or lon > 180) to prevent index failure
        try (MongoCursor<Document> cursor = users.find(Filters.type("location", "object")).iterator()) {
            while (cursor.hasNext()) {
                Document u = cursor.next();
                Document loc = (Document) u.get("location");
                if (loc == null) continue;
                @SuppressWarnings("unchecked") List<Double> coords = (List<Double>) loc.get("coordinates");
                if (coords == null || coords.size() != 2) continue;
                double lon = coords.get(0); double lat = coords.get(1);
                if (!inLat(lat) || !inLon(lon)) {
                    users.updateOne(Filters.eq("_id", u.getObjectId("_id")), new Document("$unset", new Document("location", "")));
                    System.err.println("Removed invalid geo location for user=" + u.getObjectId("_id"));
                }
            }
        }
        try {
            users.createIndex(Indexes.geo2dsphere("location"));
        } catch (Exception ex) {
            System.err.println("Failed to create geospatial index: " + ex.getMessage());
        }
    }

    public static void insertUser(String username, String email, String password, String location) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        Document newUser = new Document("username", username)
                .append("email", email)
                .append("password", password);
        if (location != null && !location.isEmpty()) {
            String[] latLong = location.split(",");
            if (latLong.length == 2) {
                try {
                    double lat = Double.parseDouble(latLong[0].trim());
                    double lon = Double.parseDouble(latLong[1].trim());
                    if (!inLat(lat) || !inLon(lon)) {
                        // attempt swap
                        if (inLat(lon) && inLon(lat)) { double tmp = lat; lat = lon; lon = tmp; }
                    }
                    if (inLat(lat) && inLon(lon)) {
                        Document locationDoc = new Document("type", "Point").append("coordinates", Arrays.asList(lon, lat));
                        newUser.append("location", locationDoc);
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        users.insertOne(newUser);
        System.out.println("User added: " + username);
    }

    public static Document loginUser(String username, String password) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        return users.find(Filters.and(Filters.eq("username", username), Filters.eq("password", password))).first();
    }

    public static Document getUserById(org.bson.types.ObjectId userId) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        return users.find(Filters.eq("_id", userId)).first();
    }

    public static void setUserHiddenStatus(org.bson.types.ObjectId userId, boolean isHidden) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        users.updateOne(Filters.eq("_id", userId), new Document("$set", new Document("isHidden", isHidden)));
    }

    public static void setUserMessagePreference(org.bson.types.ObjectId userId, boolean canReceive) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        users.updateOne(Filters.eq("_id", userId), new Document("$set", new Document("canReceiveMessages", canReceive)));
    }

    public static void deleteUser(org.bson.types.ObjectId userId) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        users.deleteOne(Filters.eq("_id", userId));
    }

    public static void updateUser(org.bson.types.ObjectId userId, String username, String email, String location) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        Document setFields = new Document("username", username).append("email", email);
        Document unsetFields = new Document();
        boolean valid = false;
        if (location != null && !location.trim().isEmpty()) {
            String[] parts = location.split(",");
            if (parts.length == 2) {
                try {
                    double lat = Double.parseDouble(parts[0].trim());
                    double lon = Double.parseDouble(parts[1].trim());
                    if (!inLat(lat) || !inLon(lon)) {
                        if (inLat(lon) && inLon(lat)) { double t = lat; lat = lon; lon = t; }
                    }
                    if (inLat(lat) && inLon(lon)) {
                        setFields.append("location", new Document("type", "Point").append("coordinates", Arrays.asList(lon, lat)));
                        valid = true;
                    }
                } catch (NumberFormatException ignored) { }
            }
        }
        if (!valid) { unsetFields.append("location", ""); }
        Document update = new Document();
        if (!setFields.isEmpty()) update.append("$set", setFields);
        if (!unsetFields.isEmpty()) update.append("$unset", unsetFields);
        if (!update.isEmpty()) users.updateOne(Filters.eq("_id", userId), update);
    }

    public static List<Pair<Document, Double>> findUsersWithinRadius(double latitude, double longitude, double radiusInKm) {
        MongoDatabase db = TestMongo.connect();
        MongoCollection<Document> users = db.getCollection("users");
        double radiusInMeters = radiusInKm * 1000;
        List<Pair<Document, Double>> usersWithDistance = new ArrayList<>();
        List<Document> pipeline = Arrays.asList(
            new Document("$geoNear",
                new Document("near", new Document("type", "Point").append("coordinates", Arrays.asList(longitude, latitude)))
                    .append("distanceField", "dist.calculated")
                    .append("maxDistance", radiusInMeters)
                    .append("spherical", true)
            )
        );
        try (MongoCursor<Document> cursor = users.aggregate(pipeline).iterator()) {
            while (cursor.hasNext()) {
                Document userDoc = cursor.next();
                Document distDoc = (Document) userDoc.get("dist");
                if (distDoc != null && distDoc.get("calculated") != null) {
                    double distanceInMeters = distDoc.getDouble("calculated");
                    usersWithDistance.add(Pair.of(userDoc, distanceInMeters / 1000.0));
                }
            }
        }
        return usersWithDistance;
    }
}
