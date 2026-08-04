package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.Map;
import java.util.TreeMap;

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
    private final String businessId; // which business this shift belongs to
    private final String title;      // the EVENT's name, e.g. "Wedding - Tomer and Maya"
    private final String description;
    private final String date;       // stored as "yyyy-MM-dd" so it sorts correctly
    private final String startTime;  // stored as "HH:mm"
    private final String endTime;    // stored as "HH:mm"
    private final String location;
    private final double latitude;   // used by the GPS feature ("X km away")
    private final double longitude;
    private final String status;
    private final String createdBy;  // uid of the manager who created it

    /**
     * How many of each staff role this shift needs, e.g. {waiter=3, cook=1}.
     *
     * A TreeMap so the roles always come out in the same order. Firestore hands maps back
     * in no particular order, and a list of roles that reshuffles itself between two
     * openings of the same screen looks broken.
     */
    private final Map<String, Integer> roleRequirements;

    // Counted from the shiftRegistrations collection, not stored on the shift.
    private int approvedWorkers;
    private int pendingWorkers;

    /** Approved people per role, so the manager can see 2 of 3 waiters are covered. */
    private final Map<String, Integer> approvedByRole = new TreeMap<>();

    public Shift(String id, String businessId, String title, String description, String date,
                 String startTime, String endTime, String location,
                 double latitude, double longitude, Map<String, Integer> roleRequirements,
                 String status, String createdBy) {
        this.id = id;
        this.businessId = businessId;
        this.title = title;
        this.description = description;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.roleRequirements = roleRequirements != null
                ? new TreeMap<>(roleRequirements)
                : new TreeMap<>();
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
                textOr(document, Constants.FIELD_BUSINESS_ID, ""),
                textOr(document, Constants.FIELD_TITLE, "Untitled shift"),
                textOr(document, Constants.FIELD_DESCRIPTION, ""),
                textOr(document, Constants.FIELD_DATE, ""),
                textOr(document, Constants.FIELD_START_TIME, ""),
                textOr(document, Constants.FIELD_END_TIME, ""),
                textOr(document, Constants.FIELD_LOCATION, ""),
                numberOr(document, Constants.FIELD_LATITUDE),
                numberOr(document, Constants.FIELD_LONGITUDE),
                roleRequirementsOf(document),
                textOr(document, Constants.FIELD_STATUS, Constants.SHIFT_OPEN),
                textOr(document, Constants.FIELD_CREATED_BY, "")
        );
    }

    /**
     * Reads the roleRequirements map off the document.
     *
     * Firestore stores whole numbers as Long and hands the map back as raw Objects, so
     * each entry is converted one at a time rather than casting the whole map. Anything
     * unreadable is skipped instead of crashing the feed -- shifts written before this
     * field existed simply come back with no role requirements.
     */
    private static Map<String, Integer> roleRequirementsOf(@NonNull DocumentSnapshot document) {
        Map<String, Integer> requirements = new TreeMap<>();
        Object raw = document.get(Constants.FIELD_ROLE_REQUIREMENTS);
        if (!(raw instanceof Map)) {
            return requirements;
        }

        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (entry.getKey() == null || !(entry.getValue() instanceof Number)) {
                continue;
            }
            int needed = ((Number) entry.getValue()).intValue();
            if (needed > 0) {
                requirements.put(entry.getKey().toString(), needed);
            }
        }
        return requirements;
    }

    private static String textOr(DocumentSnapshot document, String field, String fallback) {
        String value = document.getString(field);
        return value != null ? value : fallback;
    }

    private static double numberOr(DocumentSnapshot document, String field) {
        Double value = document.getDouble(field);
        return value != null ? value : 0d;
    }

    public String getId() { return id; }
    public String getBusinessId() { return businessId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getLocation() { return location; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getStatus() { return status; }
    public String getCreatedBy() { return createdBy; }
    public Map<String, Integer> getRoleRequirements() { return roleRequirements; }

    public int getApprovedWorkers() { return approvedWorkers; }
    public int getPendingWorkers() { return pendingWorkers; }

    public void setApprovedWorkers(int approvedWorkers) { this.approvedWorkers = approvedWorkers; }
    public void setPendingWorkers(int pendingWorkers) { this.pendingWorkers = pendingWorkers; }

    public void setApprovedForRole(String role, int count) {
        approvedByRole.put(role, count);
    }

    public int getApprovedForRole(String role) {
        Integer count = approvedByRole.get(role);
        return count != null ? count : 0;
    }

    public int getNeededForRole(String role) {
        Integer needed = roleRequirements.get(role);
        return needed != null ? needed : 0;
    }

    /** Everyone this shift needs, across all roles. */
    public int getTotalNeeded() {
        int total = 0;
        for (Integer needed : roleRequirements.values()) {
            total += needed;
        }
        return total;
    }

    /** True when every role on this shift has as many approved people as it asked for. */
    public boolean isFull() {
        int total = getTotalNeeded();
        return total > 0 && approvedWorkers >= total;
    }

    /** True when this particular role is already covered, even if others are not. */
    public boolean isRoleFull(String role) {
        int needed = getNeededForRole(role);
        return needed > 0 && getApprovedForRole(role) >= needed;
    }

    /**
     * "waiter · cook" -- the roles this shift is looking for, with no numbers.
     *
     * This is the version everybody sees. Roei's rule: the LIST of roles needed is public,
     * so an employee knows what they can apply for, but HOW MANY of each is the manager's
     * business.
     */
    public String getRoleListLabel() {
        return joinRoles(false);
    }

    /**
     * "3 waiter · 1 cook" -- the same roles with the counts. Manager-only.
     *
     * Note this is hidden in the UI, not secured. Anyone determined enough could read the
     * counts straight out of the document, so this is presentation, not privacy.
     */
    public String getRoleCountsLabel() {
        return joinRoles(true);
    }

    private String joinRoles(boolean withCounts) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : roleRequirements.entrySet()) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            if (withCounts) {
                text.append(getApprovedForRole(entry.getKey()))
                        .append("/")
                        .append(entry.getValue())
                        .append(" ");
            }
            text.append(entry.getKey());
        }
        return text.toString();
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
