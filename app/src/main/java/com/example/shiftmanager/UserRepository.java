package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Firestore read and write to do with people lives here, the same way
 * ShiftRepository owns everything to do with shifts.
 */
public class UserRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /** Reads one person, used to find out the signed-in user's display name and role. */
    public void loadUser(@NonNull String userId, @NonNull final Callback<AppUser> callback) {
        db.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError(new IllegalStateException("No profile for this user"));
                        return;
                    }
                    callback.onSuccess(AppUser.fromDocument(document));
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Writes the profile collected on the sign-up screen.
     *
     * The document id is the Firebase Auth uid, so a person's profile and their login are
     * the same record and there is never a second copy to keep in step.
     *
     * The role is decided here rather than being asked for: signing in with the owner's
     * address makes you the admin, and nobody else can become one.
     */
    public void createProfile(@NonNull String userId, @NonNull String name,
                              @NonNull String email, @NonNull String chosenRole,
                              @NonNull final Callback<AppUser> callback) {
        final String role = Constants.ADMIN_EMAIL.equalsIgnoreCase(email)
                ? Constants.ROLE_ADMIN
                : chosenRole;

        // Stored lower-cased so a manager inviting "Dana@Work.com" still finds the account
        // that signed up as "dana@work.com". Firebase treats addresses that way too.
        final String storedEmail = email.trim().toLowerCase();

        Map<String, Object> profile = new HashMap<>();
        profile.put(Constants.FIELD_NAME, name);
        profile.put(Constants.FIELD_EMAIL, storedEmail);
        profile.put(Constants.FIELD_ROLE, role);
        profile.put(Constants.FIELD_ACTIVE, true);
        profile.put(Constants.FIELD_ACTIVE_BUSINESS_ID, "");
        profile.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .set(profile)
                .addOnSuccessListener(unused ->
                        callback.onSuccess(new AppUser(userId, name, storedEmail, role, true, "")))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Remembers which business the user is currently looking at.
     *
     * Stored on the user rather than held in memory so the choice survives closing the
     * app -- otherwise somebody in three businesses would have to re-pick every launch.
     */
    public void setActiveBusiness(@NonNull String userId, @NonNull String businessId,
                                  @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_USERS)
                .document(userId)
                .update(Constants.FIELD_ACTIVE_BUSINESS_ID, businessId)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Finds a person by their email address, so a manager can invite somebody who already
     * has an account. Returns null when nobody matches, which is a normal answer.
     */
    public void findUserByEmail(@NonNull String email,
                                @NonNull final Callback<AppUser> callback) {
        db.collection(Constants.COLLECTION_USERS)
                .whereEqualTo(Constants.FIELD_EMAIL, email.trim().toLowerCase())
                .get()
                .addOnSuccessListener(documents -> {
                    if (documents.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(AppUser.fromDocument(documents.getDocuments().get(0)));
                })
                .addOnFailureListener(callback::onError);
    }

    /** Every account in the app -- the admin view, and nowhere else. */
    public void loadAllUsers(@NonNull final Callback<List<AppUser>> callback) {
        db.collection(Constants.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(documents -> {
                    List<AppUser> users = new ArrayList<>();
                    for (QueryDocumentSnapshot document : documents) {
                        users.add(AppUser.fromDocument(document));
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(callback::onError);
    }

}
