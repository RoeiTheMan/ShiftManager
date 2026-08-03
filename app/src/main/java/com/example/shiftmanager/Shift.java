package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * One work shift, exactly as it is stored in the Firestore "shifts" collection.
 *
 * Every value on this object comes from the database. Nothing here is hardcoded --
 * that is a requirement of the final project ("No Static Data allowed, all data
 * should be kept in a Database").
 *
 * The approved/pending counts are the one exception: they are not stored on the
 * shift document itself, because a registration belongs to a combination of one
 * employee + one shift + one status. They live in the "shiftRegistrations"
 * collection and are counted into this object after the shift is loaded.
 */
public class Shift {

    private final String id;
    private final String title;
    private final String description;
    private final String date;       // stored as "yyyy-MM-dd" so it sorts correctly
    private final String startTime;  // stored as "HH:mm"
    private final String endTime;    // stored as "HH:mm"
    private final String location;
    private final double latitude;   // used by the GPS feature ("X km away")
    private final double longitude;
    private final int maxWorkers;
    private final String status;
    private final String createdBy;  // uid of the manager who created it

    // Counted from the shiftRegistrations collection, not stored on the shift.
    private int approvedWorkers;
    private int pendingWorkers;

    public Shift(String id, String title, String description, String date,
                 String startTime, String endTime, String location,
                 double latitude, double longitude, int maxWorkers,
                 String status, String createdBy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxWorkers = maxWorkers;
        this.status = status;
        this.createdBy = createdBy;
    }

    /**
     * Builds a Shift out of a Firestore document.
     *
     * All the null-checking lives here, in one place, so the Activities never have
     * to worry about a field being missing. A shift saved by an older version of
     * the app (for example the single "Morning Shift" document created for HW3,
     * which only had a title) still loads instead of crashing the feed.
     */
    @NonNull
    public static Shift fromDocument(@NonNull DocumentSnapshot document) {
        return new Shift(
                document.getId(),
                textOr(document, Constants.FIELD_TITLE, "Untitled shift"),
                textOr(document, Constants.FIELD_DESCRIPTION, ""),
                textOr(document, Constants.FIELD_DATE, ""),
                textOr(document, Constants.FIELD_START_TIME, ""),
                textOr(document, Constants.FIELD_END_TIME, ""),
                textOr(document, Constants.FIELD_LOCATION, ""),
                numberOr(document, Constants.FIELD_LATITUDE),
                numberOr(document, Constants.FIELD_LONGITUDE),
                (int) wholeNumberOr(document, Constants.FIELD_MAX_WORKERS),
                textOr(document, Constants.FIELD_STATUS, Constants.SHIFT_OPEN),
                textOr(document, Constants.FIELD_CREATED_BY, "")
        );
    }

    private static String textOr(DocumentSnapshot document, String field, String fallback) {
        String value = document.getString(field);
        return value != null ? value : fallback;
    }

    private static double numberOr(DocumentSnapshot document, String field) {
        Double value = document.getDouble(field);
        return value != null ? value : 0d;
    }

    private static long wholeNumberOr(DocumentSnapshot document, String field) {
        Long value = document.getLong(field);
        return value != null ? value : 0L;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getLocation() { return location; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public int getMaxWorkers() { return maxWorkers; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }

    public int getApprovedWorkers() { return approvedWorkers; }
    public int getPendingWorkers() { return pendingWorkers; }

    public void setApprovedWorkers(int approvedWorkers) { this.approvedWorkers = approvedWorkers; }
    public void setPendingWorkers(int pendingWorkers) { this.pendingWorkers = pendingWorkers; }

    /** True when the shift has as many approved workers as it can take. */
    public boolean isFull() {
        return maxWorkers > 0 && approvedWorkers >= maxWorkers;
    }

    /** "09:00 - 17:00", or an empty string when the shift has no times saved. */
    public String getTimeRange() {
        if (startTime.isEmpty() && endTime.isEmpty()) {
            return "";
        }
        return startTime + " - " + endTime;
    }

    /** True when a real map position was saved, so the distance can be worked out. */
    public boolean hasCoordinates() {
        return latitude != 0d || longitude != 0d;
    }
}
