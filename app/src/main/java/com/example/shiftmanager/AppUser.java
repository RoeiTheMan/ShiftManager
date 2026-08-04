package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * One person in the business, from the "users" collection.
 *
 * A user document is written the first time somebody signs in and picks a role, so the
 * Staff Directory does not need a separate employee list -- everyone who has ever used
 * the app is already here.
 *
 * The class is called AppUser rather than User because Firebase already has a class
 * named FirebaseUser, and two similarly named types in the same screen is a good way to
 * import the wrong one by mistake.
 */
public class AppUser {

    private final String id;
    private final String name;
    private final String email;
    private final String role;
    private final boolean active;
    private final String activeBusinessId;

    public AppUser(String id, String name, String email, String role, boolean active,
                   String activeBusinessId) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
        this.active = active;
        this.activeBusinessId = activeBusinessId;
    }

    @NonNull
    public static AppUser fromDocument(@NonNull DocumentSnapshot document) {
        String name = document.getString(Constants.FIELD_NAME);
        String email = document.getString(Constants.FIELD_EMAIL);
        String role = document.getString(Constants.FIELD_ROLE);
        Boolean active = document.getBoolean(Constants.FIELD_ACTIVE);
        String activeBusinessId = document.getString(Constants.FIELD_ACTIVE_BUSINESS_ID);

        return new AppUser(
                document.getId(),
                name != null ? name : "Unnamed",
                email != null ? email : "",
                role != null ? role : Constants.ROLE_EMPLOYEE,
                active == null || active,
                activeBusinessId != null ? activeBusinessId : ""
        );
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public String getActiveBusinessId() { return activeBusinessId; }

    public boolean isManager() {
        return Constants.ROLE_MANAGER.equals(role);
    }

    public boolean isAdmin() {
        return Constants.ROLE_ADMIN.equals(role);
    }

    /** True once the user has picked which business they are working in. */
    public boolean hasActiveBusiness() {
        return !activeBusinessId.isEmpty();
    }

    /** The line shown under the app name in every header: "Manager" / "Employee" / "Admin". */
    public String getRoleLabel() {
        if (isAdmin()) {
            return "Admin";
        }
        return isManager() ? "Manager" : "Employee";
    }
}
