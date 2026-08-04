package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * One business (a work environment) from the "businesses" collection.
 *
 * This is the entity everything else hangs off. A shift belongs to a business, a person
 * belongs to a business through a membership, and the staff roles a shift can ask for are
 * the ones this business defined when it was set up.
 *
 * Without it, users float free with nothing to belong to -- which is exactly the problem
 * this class was added to fix.
 */
public class Business {

    private final String id;
    private final String name;
    private final String type;
    private final String address;
    private final String description;
    private final List<String> staffRoles;
    private final String createdBy;

    public Business(String id, String name, String type, String address,
                    String description, List<String> staffRoles, String createdBy) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.address = address;
        this.description = description;
        this.staffRoles = staffRoles;
        this.createdBy = createdBy;
    }

    /**
     * Builds a Business from a Firestore document, defaulting every missing field.
     *
     * All the null-handling lives here on purpose: a document written by an older version
     * of the app is missing fields, and a screen that reads them directly would crash on
     * one of them. Doing it in one place means the screens can just use the values.
     */
    @NonNull
    public static Business fromDocument(@NonNull DocumentSnapshot document) {
        String name = document.getString(Constants.FIELD_NAME);
        String type = document.getString(Constants.FIELD_BUSINESS_TYPE);
        String address = document.getString(Constants.FIELD_BUSINESS_ADDRESS);
        String description = document.getString(Constants.FIELD_DESCRIPTION);
        String createdBy = document.getString(Constants.FIELD_CREATED_BY);

        // Firestore hands arrays back as a raw List, so copy the entries out one at a
        // time rather than casting the whole thing and hoping every element is a String.
        List<String> roles = new ArrayList<>();
        Object rawRoles = document.get(Constants.FIELD_STAFF_ROLES);
        if (rawRoles instanceof List) {
            for (Object role : (List<?>) rawRoles) {
                if (role != null) {
                    roles.add(role.toString());
                }
            }
        }

        return new Business(
                document.getId(),
                name != null ? name : "Unnamed business",
                type != null ? type : "",
                address != null ? address : "",
                description != null ? description : "",
                roles,
                createdBy != null ? createdBy : ""
        );
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public List<String> getStaffRoles() { return staffRoles; }
    public String getCreatedBy() { return createdBy; }

    /** The line shown under the business name in search results and the switcher. */
    public String getSubtitle() {
        if (!type.isEmpty() && !address.isEmpty()) {
            return type + " · " + address;
        }
        if (!type.isEmpty()) {
            return type;
        }
        return address;
    }
}
