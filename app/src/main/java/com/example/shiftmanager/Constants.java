package com.example.shiftmanager;

/**
 * Central place for every Firestore collection name, field name and fixed value
 * used across the app.
 *
 * Why this file exists: without it the same strings ("shifts", "pending", ...) get
 * retyped in every Activity, and a single typo silently reads or writes the wrong
 * place instead of failing the build. Declaring them once means the compiler
 * catches mistakes for us.
 */
public final class Constants {

    // This class only holds constants, so nobody should ever create an instance of it.
    private Constants() {
    }

    // ---------- Firestore collections ----------

    public static final String COLLECTION_USERS = "users";
    public static final String COLLECTION_SHIFTS = "shifts";
    public static final String COLLECTION_REGISTRATIONS = "shiftRegistrations";

    // ---------- Fields on a user document ----------

    public static final String FIELD_NAME = "name";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_ACTIVE = "active";

    // ---------- Fields on a shift document ----------

    public static final String FIELD_TITLE = "title";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_DATE = "date";
    public static final String FIELD_START_TIME = "startTime";
    public static final String FIELD_END_TIME = "endTime";
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_LATITUDE = "latitude";
    public static final String FIELD_LONGITUDE = "longitude";
    public static final String FIELD_MAX_WORKERS = "maxWorkers";
    public static final String FIELD_STATUS = "status";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_CREATED_AT = "createdAt";

    // ---------- Fields on a shift-registration document ----------

    public static final String FIELD_SHIFT_ID = "shiftId";
    public static final String FIELD_EMPLOYEE_ID = "employeeId";
    public static final String FIELD_EMPLOYEE_NAME = "employeeName";
    public static final String FIELD_NOTE = "note";

    // ---------- User roles ----------

    public static final String ROLE_MANAGER = "manager";
    public static final String ROLE_EMPLOYEE = "employee";

    // ---------- Shift statuses ----------

    public static final String SHIFT_OPEN = "open";
    public static final String SHIFT_FULL = "full";
    public static final String SHIFT_CLOSED = "closed";
    public static final String SHIFT_COMPLETED = "completed";
    public static final String SHIFT_CANCELLED = "cancelled";

    // ---------- Registration statuses ----------

    public static final String REGISTRATION_PENDING = "pending";
    public static final String REGISTRATION_APPROVED = "approved";
    public static final String REGISTRATION_REJECTED = "rejected";
    public static final String REGISTRATION_CANCELLED = "cancelled";

    // ---------- Intent extras used when moving between screens ----------

    public static final String EXTRA_SHIFT_ID = "extra_shift_id";
}
