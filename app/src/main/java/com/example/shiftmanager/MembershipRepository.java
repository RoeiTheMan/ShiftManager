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
 * Every Firestore read and write to do with who belongs to which business.
 *
 * None of the queries here combine a where clause with an orderBy. That is deliberate:
 * Firestore demands a hand-built composite index for those, and the app would break with a
 * permission-style error the first time a screen opened. Sorting happens in Java instead,
 * on lists that are only ever a few dozen entries long.
 */
public class MembershipRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    /**
     * Records that somebody wants to be in a business, or already is.
     *
     * Both join directions use this. An employee tapping "request to join" passes
     * REQUESTED_BY_EMPLOYEE and waits for a manager; a manager adding somebody passes
     * REQUESTED_BY_MANAGER and waits for that person. The manager who creates a business
     * is the one case that skips the wait, by passing approved straight in.
     */
    public void createMembership(@NonNull String businessId, @NonNull AppUser user,
                                 @NonNull String requestedBy, @NonNull String status,
                                 @NonNull final Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_BUSINESS_ID, businessId);
        data.put(Constants.FIELD_USER_ID, user.getId());
        // The name and email are copied onto the membership so the team list can be drawn
        // from one query instead of one extra read per person.
        data.put(Constants.FIELD_USER_NAME, user.getName());
        data.put(Constants.FIELD_USER_EMAIL, user.getEmail());
        data.put(Constants.FIELD_ROLE, user.getRole());
        data.put(Constants.FIELD_STATUS, status);
        data.put(Constants.FIELD_REQUESTED_BY, requestedBy);
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_MEMBERSHIPS)
                .add(data)
                .addOnSuccessListener(reference -> callback.onSuccess(reference.getId()))
                .addOnFailureListener(callback::onError);
    }

    /** Every business this person has joined, been invited to, or asked to join. */
    public void loadMembershipsForUser(@NonNull String userId,
                                       @NonNull final Callback<List<Membership>> callback) {
        db.collection(Constants.COLLECTION_MEMBERSHIPS)
                .whereEqualTo(Constants.FIELD_USER_ID, userId)
                .get()
                .addOnSuccessListener(documents -> callback.onSuccess(toList(documents)))
                .addOnFailureListener(callback::onError);
    }

    /** Everyone attached to one business, whatever their status -- the team screen. */
    public void loadMembershipsForBusiness(@NonNull String businessId,
                                           @NonNull final Callback<List<Membership>> callback) {
        db.collection(Constants.COLLECTION_MEMBERSHIPS)
                .whereEqualTo(Constants.FIELD_BUSINESS_ID, businessId)
                .get()
                .addOnSuccessListener(documents -> callback.onSuccess(toList(documents)))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Looks for an existing link between this person and this business.
     *
     * Called before creating a new one, so that asking to join twice does not leave two
     * pending requests for the same person sitting in the manager's list.
     */
    public void findMembership(@NonNull String businessId, @NonNull String userId,
                               @NonNull final Callback<Membership> callback) {
        db.collection(Constants.COLLECTION_MEMBERSHIPS)
                .whereEqualTo(Constants.FIELD_BUSINESS_ID, businessId)
                .whereEqualTo(Constants.FIELD_USER_ID, userId)
                .get()
                .addOnSuccessListener(documents -> {
                    // A null result means "no link yet", which is a normal answer here and
                    // not an error -- the caller decides what to do about it.
                    if (documents.isEmpty()) {
                        callback.onSuccess(null);
                        return;
                    }
                    callback.onSuccess(Membership.fromDocument(documents.getDocuments().get(0)));
                })
                .addOnFailureListener(callback::onError);
    }

    /** Answers a pending request, from whichever side was being waited on. */
    public void setStatus(@NonNull String membershipId, @NonNull String status,
                          @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_MEMBERSHIPS)
                .document(membershipId)
                .update(Constants.FIELD_STATUS, status)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Takes somebody out of a business, and cancels any shift they had signed up for there.
     *
     * Leaving the registrations behind would keep them counted against a shift's role
     * requirements and would still show their name on the approval screen after they left.
     */
    public void removeMembership(@NonNull final String membershipId,
                                 @NonNull final String businessId,
                                 @NonNull final String userId,
                                 @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_BUSINESS_ID, businessId)
                .whereEqualTo(Constants.FIELD_EMPLOYEE_ID, userId)
                .get()
                .addOnSuccessListener(registrations -> {
                    for (QueryDocumentSnapshot registration : registrations) {
                        registration.getReference().delete();
                    }
                    db.collection(Constants.COLLECTION_MEMBERSHIPS)
                            .document(membershipId)
                            .delete()
                            .addOnSuccessListener(callback::onSuccess)
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    private List<Membership> toList(@NonNull Iterable<QueryDocumentSnapshot> documents) {
        List<Membership> memberships = new ArrayList<>();
        for (QueryDocumentSnapshot document : documents) {
            memberships.add(Membership.fromDocument(document));
        }
        return memberships;
    }
}
