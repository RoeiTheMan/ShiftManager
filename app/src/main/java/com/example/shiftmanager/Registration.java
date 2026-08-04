package com.example.shiftmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * One employee's application to work one shift, from the "shiftRegistrations" collection.
 *
 * This is deliberately its own collection rather than a flag on the shift. A sign-up is a
 * fact about a *combination* of three things -- which employee, which shift, and what the
 * manager has decided so far -- so it needs its own record. The earlier version of the app
 * used a boolean held in memory on the Shift object, which meant a sign-up disappeared the
 * moment the screen was rebuilt.
 */
public class Registration {

    private final String id;
    private final String shiftId;
    private final String businessId;
    private final String employeeId;
    private final String employeeName;

    /**
     * Which staff role they applied for -- waiter, cook, and so on.
     *
     * A shift asks for a specific mix (3 waiters, 1 cook), so an application has to say
     * which slot it is for. Approving somebody is approving them as that role.
     */
    private final String role;

    private final String status;
    private final String note;

    /**
     * When the employee checked in for this shift, or 0 if they have not.
     *
     * Check-in is the app's use of GPS: an approved employee taps "Check in" when they
     * start work, and the phone's position and the time are stamped onto the registration.
     * That gives the manager evidence of attendance without anyone filling in a timesheet.
     */
    private final long checkedInAt;

    /** How far from the shift's saved position they were, in metres. -1 when unknown. */
    private final int checkInDistanceMetres;

    /** Filled in after loading, so a screen can show the shift behind this registration. */
    @Nullable
    private Shift shift;

    public Registration(String id, String shiftId, String businessId, String employeeId,
                        String employeeName, String role, String status, String note,
                        long checkedInAt, int checkInDistanceMetres) {
        this.id = id;
        this.shiftId = shiftId;
        this.businessId = businessId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.role = role;
        this.status = status;
        this.note = note;
        this.checkedInAt = checkedInAt;
        this.checkInDistanceMetres = checkInDistanceMetres;
    }

    @NonNull
    public static Registration fromDocument(@NonNull DocumentSnapshot document) {
        return new Registration(
                document.getId(),
                textOr(document, Constants.FIELD_SHIFT_ID, ""),
                textOr(document, Constants.FIELD_BUSINESS_ID, ""),
                textOr(document, Constants.FIELD_EMPLOYEE_ID, ""),
                textOr(document, Constants.FIELD_EMPLOYEE_NAME, "Unnamed employee"),
                textOr(document, Constants.FIELD_ROLE, ""),
                textOr(document, Constants.FIELD_STATUS, Constants.REGISTRATION_PENDING),
                textOr(document, Constants.FIELD_NOTE, ""),
                timestampMillis(document, Constants.FIELD_CHECKED_IN_AT),
                (int) longOr(document, Constants.FIELD_CHECKIN_DISTANCE, -1L)
        );
    }

    /**
     * Reads checkedInAt, which Firestore stores as a server Timestamp rather than a number.
     *
     * Returns 0 both when the field is missing and in the brief window right after a write
     * when the server has not yet filled the value in -- in both cases we genuinely do not
     * have a check-in time to show yet.
     */
    private static long timestampMillis(DocumentSnapshot document, String field) {
        com.google.firebase.Timestamp value = document.getTimestamp(field);
        return value != null ? value.toDate().getTime() : 0L;
    }

    private static String textOr(DocumentSnapshot document, String field, String fallback) {
        String value = document.getString(field);
        return value != null ? value : fallback;
    }

    private static long longOr(DocumentSnapshot document, String field, long fallback) {
        Long value = document.getLong(field);
        return value != null ? value : fallback;
    }

    public String getId() { return id; }
    public String getShiftId() { return shiftId; }
    public String getBusinessId() { return businessId; }
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getNote() { return note; }

    /**
     * "Dana Levi · waiter", or just the name for registrations made before roles existed.
     *
     * Falling back to the bare name matters: the old test data has no role, and printing
     * a trailing separator with nothing after it would look like a rendering bug.
     */
    public String getNameWithRole() {
        return role.isEmpty() ? employeeName : employeeName + " · " + role;
    }

    @Nullable
    public Shift getShift() { return shift; }

    public void setShift(@Nullable Shift shift) { this.shift = shift; }

    public boolean isApproved() {
        return Constants.REGISTRATION_APPROVED.equals(status);
    }

    public boolean isPending() {
        return Constants.REGISTRATION_PENDING.equals(status);
    }

    public long getCheckedInAt() { return checkedInAt; }

    public int getCheckInDistanceMetres() { return checkInDistanceMetres; }

    /** True once the employee has checked in for this shift. */
    public boolean isCheckedIn() {
        return checkedInAt > 0L;
    }

    /**
     * Only an approved employee can check in.
     *
     * Someone still waiting on the manager has not been given the shift, so letting them
     * stamp an arrival would record attendance for work they were never assigned.
     */
    public boolean canCheckIn() {
        return isApproved() && !isCheckedIn();
    }

    /** "14:05", or an empty string if they have not checked in. */
    public String getCheckInTimeText() {
        if (!isCheckedIn()) {
            return "";
        }
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                .format(new java.util.Date(checkedInAt));
    }
}
