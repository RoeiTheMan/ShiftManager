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
    public static final String COLLECTION_BUSINESSES = "businesses";
    public static final String COLLECTION_MEMBERSHIPS = "businessMemberships";

    // ---------- Fields on a user document ----------

    public static final String FIELD_NAME = "name";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_ROLE = "role";
    public static final String FIELD_ACTIVE = "active";

    // Which business the user is currently looking at. A user may belong to several,
    // so one of them has to be "the current one" for every screen to have a subject.
    public static final String FIELD_ACTIVE_BUSINESS_ID = "activeBusinessId";

    // ---------- Fields on a business document ----------

    public static final String FIELD_BUSINESS_TYPE = "type";
    public static final String FIELD_BUSINESS_ADDRESS = "address";

    // The staff roles this business actually uses (waiter, cook, bartender...).
    // Defined once when the business is set up, then picked from when staffing a shift.
    public static final String FIELD_STAFF_ROLES = "staffRoles";

    // Lower-cased copy of the name. Firestore cannot do case-insensitive matching, so the
    // business search bar queries this field instead of the display name.
    public static final String FIELD_NAME_LOWER = "nameLower";

    // ---------- Fields on a membership document ----------

    public static final String FIELD_BUSINESS_ID = "businessId";
    public static final String FIELD_USER_ID = "userId";
    public static final String FIELD_USER_NAME = "userName";
    public static final String FIELD_USER_EMAIL = "userEmail";
    public static final String FIELD_REQUESTED_BY = "requestedBy";

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

    // How many of each staff role this shift needs, e.g. {waiter: 3, cook: 1}.
    // Replaces the single maxWorkers headcount: a shift is not "6 people", it is
    // "3 waiters, 1 cook, 2 bartenders", and each is filled independently.
    public static final String FIELD_ROLE_REQUIREMENTS = "roleRequirements";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_CREATED_AT = "createdAt";

    // ---------- Fields on a shift-registration document ----------

    public static final String FIELD_SHIFT_ID = "shiftId";
    public static final String FIELD_EMPLOYEE_ID = "employeeId";
    public static final String FIELD_EMPLOYEE_NAME = "employeeName";
    public static final String FIELD_NOTE = "note";

    // Written when an approved employee checks in at the start of their shift.
    public static final String FIELD_CHECKED_IN_AT = "checkedInAt";
    public static final String FIELD_CHECKIN_LATITUDE = "checkInLatitude";
    public static final String FIELD_CHECKIN_LONGITUDE = "checkInLongitude";
    public static final String FIELD_CHECKIN_DISTANCE = "checkInDistanceMetres";

    // ---------- User roles ----------

    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_MANAGER = "manager";
    public static final String ROLE_EMPLOYEE = "employee";

    /**
     * The app owner. Anyone signing in with this address becomes the admin automatically,
     * which is why there is no "become an admin" button anywhere in the UI.
     *
     * Hard-coding it is deliberate: the admin is one specific real person, not a role a
     * user can apply for, so there is nothing to configure and nothing to abuse.
     */
    public static final String ADMIN_EMAIL = "roeilustig@gmail.com";

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

    // ---------- Membership statuses ----------

    // Somebody has asked to be in a business, and the other side has not answered yet.
    // Which side still has to answer is decided by requestedBy, below.
    public static final String MEMBERSHIP_PENDING = "pending";
    public static final String MEMBERSHIP_APPROVED = "approved";
    public static final String MEMBERSHIP_REJECTED = "rejected";

    // Who started the request -- this is what makes the approval run the right way round.
    // An employee asking to join is approved by the manager; a manager adding someone is
    // approved by that employee. Same document, opposite directions.
    public static final String REQUESTED_BY_EMPLOYEE = "employee";
    public static final String REQUESTED_BY_MANAGER = "manager";

    // ---------- Intent extras used when moving between screens ----------

    public static final String EXTRA_SHIFT_ID = "extra_shift_id";
    public static final String EXTRA_BUSINESS_ID = "extra_business_id";
}
