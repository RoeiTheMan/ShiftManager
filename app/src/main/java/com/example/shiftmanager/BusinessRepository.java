package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Firestore read and write to do with businesses, the same way ShiftRepository owns
 * shifts and UserRepository owns people.
 */
public class BusinessRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Creates a business and hands back its new id so the caller can immediately make the
     * creator its first manager.
     */
    public void createBusiness(@NonNull String name, @NonNull String type,
                               @NonNull String address, @NonNull String description,
                               @NonNull List<String> staffRoles, @NonNull String createdBy,
                               @NonNull final Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_NAME, name);
        // Stored alongside the real name purely so the search bar can match case-insensitively.
        data.put(Constants.FIELD_NAME_LOWER, name.toLowerCase());
        data.put(Constants.FIELD_BUSINESS_TYPE, type);
        data.put(Constants.FIELD_BUSINESS_ADDRESS, address);
        data.put(Constants.FIELD_DESCRIPTION, description);
        data.put(Constants.FIELD_STAFF_ROLES, staffRoles);
        data.put(Constants.FIELD_CREATED_BY, createdBy);
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_BUSINESSES)
                .add(data)
                .addOnSuccessListener(reference -> callback.onSuccess(reference.getId()))
                .addOnFailureListener(callback::onError);
    }

    /**
     * The business search bar: returns every business whose name starts with what has been
     * typed so far, so the list narrows as the user types.
     *
     * Firestore has no "contains" or case-insensitive operator, so this is a range query
     * over the lower-cased name. \uf8ff is a very high Unicode character, which makes
     * "starts with cafe" read as "everything from 'cafe' up to 'cafe' + anything".
     */
    public void searchBusinesses(@NonNull String query,
                                 @NonNull final Callback<List<Business>> callback) {
        String prefix = query.trim().toLowerCase();

        Query firestoreQuery = db.collection(Constants.COLLECTION_BUSINESSES);
        if (!prefix.isEmpty()) {
            firestoreQuery = firestoreQuery
                    .whereGreaterThanOrEqualTo(Constants.FIELD_NAME_LOWER, prefix)
                    .whereLessThanOrEqualTo(Constants.FIELD_NAME_LOWER, prefix + "\uf8ff");
        }

        firestoreQuery
                .orderBy(Constants.FIELD_NAME_LOWER)
                .limit(25)
                .get()
                .addOnSuccessListener(documents -> {
                    List<Business> businesses = new ArrayList<>();
                    for (QueryDocumentSnapshot document : documents) {
                        businesses.add(Business.fromDocument(document));
                    }
                    callback.onSuccess(businesses);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Reads one business, used to show its name and staff roles on the shift screens. */
    public void loadBusiness(@NonNull String businessId,
                             @NonNull final Callback<Business> callback) {
        db.collection(Constants.COLLECTION_BUSINESSES)
                .document(businessId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError(new IllegalStateException("That business no longer exists"));
                        return;
                    }
                    callback.onSuccess(Business.fromDocument(document));
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Loads several businesses by id, for the switcher and for the admin screen.
     *
     * Firestore has no "get these ids" call, so this reads them one at a time and waits
     * for the last one. The results are collected in a list that is only handed back once
     * every read has finished, successfully or not -- otherwise a single missing business
     * would leave the caller waiting forever.
     */
    public void loadBusinesses(@NonNull List<String> businessIds,
                               @NonNull final Callback<List<Business>> callback) {
        final List<Business> found = new ArrayList<>();
        if (businessIds.isEmpty()) {
            callback.onSuccess(found);
            return;
        }

        final int[] remaining = {businessIds.size()};
        for (String businessId : businessIds) {
            db.collection(Constants.COLLECTION_BUSINESSES)
                    .document(businessId)
                    .get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null
                                && task.getResult().exists()) {
                            found.add(Business.fromDocument(task.getResult()));
                        }
                        remaining[0]--;
                        if (remaining[0] == 0) {
                            callback.onSuccess(found);
                        }
                    });
        }
    }

    /** Every business in the app -- the admin view, and nowhere else. */
    public void loadAllBusinesses(@NonNull final Callback<List<Business>> callback) {
        db.collection(Constants.COLLECTION_BUSINESSES)
                .orderBy(Constants.FIELD_NAME_LOWER)
                .get()
                .addOnSuccessListener(documents -> {
                    List<Business> businesses = new ArrayList<>();
                    for (QueryDocumentSnapshot document : documents) {
                        businesses.add(Business.fromDocument(document));
                    }
                    callback.onSuccess(businesses);
                })
                .addOnFailureListener(callback::onError);
    }
}
