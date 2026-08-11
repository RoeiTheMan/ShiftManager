package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Firestore read and write to do with shifts and registrations lives here.
 *
 * Why it is a separate class: before this, the manager screen and the employee screen
 * each held their own copy of the same query, so a change had to be made twice and the
 * two copies drifted apart. Keeping the database code in one place means the Activities
 * only deal with showing things on screen, which is the "separation of responsibilities"
 * principle from the course.
 */
public class ShiftRepository {

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ---------------------------------------------------------------- reading

    /**
     * Loads every shift, oldest date first, and fills in each shift's approved and
     * pending worker counts from the shiftRegistrations collection.
     *
     * The counts are worked out here rather than stored on the shift document, because
     * storing them would mean two places could disagree about the same fact. The whole
     * registrations collection is small for this app, so counting on the fly is simpler
     * and always correct.
     */
    public void loadShiftsWithCounts(@NonNull String businessId,
                                     @NonNull final Callback<List<Shift>> callback) {
        db.collection(Constants.COLLECTION_SHIFTS)
                .whereEqualTo(Constants.FIELD_BUSINESS_ID, businessId)
                .get()
                .addOnSuccessListener(shiftDocs -> {
                    final List<Shift> shifts = new ArrayList<>();
                    for (QueryDocumentSnapshot document : shiftDocs) {
                        shifts.add(Shift.fromDocument(document));
                    }

                    // Sorted here rather than with orderBy, because combining a where
                    // clause with an orderBy makes Firestore demand a hand-built composite
                    // index -- the feed would fail on first open until somebody created it.
                    // A business has tens of shifts, not thousands, so this costs nothing.
                    Collections.sort(shifts, (a, b) -> a.getDate().compareTo(b.getDate()));

                    if (shifts.isEmpty()) {
                        callback.onSuccess(shifts);
                        return;
                    }

                    applyRegistrationCounts(shifts, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Second half of loadShiftsWithCounts: tally registrations onto the loaded shifts. */
    private void applyRegistrationCounts(final List<Shift> shifts,
                                         final Callback<List<Shift>> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .get()
                .addOnSuccessListener(registrationDocs -> {
                    Map<String, Integer> approved = new HashMap<>();
                    Map<String, Integer> pending = new HashMap<>();
                    // Keyed "shiftId|role", so one pass counts both the shift totals and
                    // the per-role breakdown the manager needs.
                    Map<String, Integer> approvedPerRole = new HashMap<>();

                    for (QueryDocumentSnapshot registration : registrationDocs) {
                        String shiftId = registration.getString(Constants.FIELD_SHIFT_ID);
                        String status = registration.getString(Constants.FIELD_STATUS);
                        String role = registration.getString(Constants.FIELD_ROLE);
                        if (shiftId == null || status == null) {
                            continue;
                        }
                        if (Constants.REGISTRATION_APPROVED.equals(status)) {
                            increment(approved, shiftId);
                            if (role != null) {
                                increment(approvedPerRole, shiftId + "|" + role);
                            }
                        } else if (Constants.REGISTRATION_PENDING.equals(status)) {
                            increment(pending, shiftId);
                        }
                    }

                    for (Shift shift : shifts) {
                        Integer a = approved.get(shift.getId());
                        Integer p = pending.get(shift.getId());
                        shift.setApprovedWorkers(a != null ? a : 0);
                        shift.setPendingWorkers(p != null ? p : 0);

                        for (String role : shift.getRoleRequirements().keySet()) {
                            Integer perRole = approvedPerRole.get(shift.getId() + "|" + role);
                            shift.setApprovedForRole(role, perRole != null ? perRole : 0);
                        }
                    }

                    callback.onSuccess(shifts);
                })
                .addOnFailureListener(error -> {
                    // The shifts themselves loaded fine; only the counts are missing.
                    // Showing the feed without counts beats showing an error page.
                    callback.onSuccess(shifts);
                });
    }

    private void increment(Map<String, Integer> counts, String key) {
        Integer current = counts.get(key);
        counts.put(key, current != null ? current + 1 : 1);
    }

    /** Loads a single shift, for the detail screen. */
    public void loadShift(@NonNull String shiftId, @NonNull final Callback<Shift> callback) {
        db.collection(Constants.COLLECTION_SHIFTS)
                .document(shiftId)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        callback.onError(new IllegalStateException("Shift no longer exists"));
                        return;
                    }
                    callback.onSuccess(Shift.fromDocument(document));
                })
                .addOnFailureListener(callback::onError);
    }

    // ---------------------------------------------------------------- writing

    /** Saves a brand new shift and hands back its generated id. */
    public void createShift(@NonNull Shift shift, @NonNull final Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_BUSINESS_ID, shift.getBusinessId());
        data.put(Constants.FIELD_TITLE, shift.getTitle());
        data.put(Constants.FIELD_DESCRIPTION, shift.getDescription());
        data.put(Constants.FIELD_DATE, shift.getDate());
        data.put(Constants.FIELD_START_TIME, shift.getStartTime());
        data.put(Constants.FIELD_END_TIME, shift.getEndTime());
        data.put(Constants.FIELD_LOCATION, shift.getLocation());
        data.put(Constants.FIELD_LATITUDE, shift.getLatitude());
        data.put(Constants.FIELD_LONGITUDE, shift.getLongitude());
        data.put(Constants.FIELD_ROLE_REQUIREMENTS, shift.getRoleRequirements());
        data.put(Constants.FIELD_STATUS, Constants.SHIFT_OPEN);
        data.put(Constants.FIELD_CREATED_BY, shift.getCreatedBy());
        // Server time, not the phone's: a device with a wrong clock would otherwise
        // write a createdAt that sorts records into the wrong order.
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_SHIFTS)
                .add(data)
                .addOnSuccessListener(reference -> callback.onSuccess(reference.getId()))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Saves changes to an existing shift.
     *
     * Only the fields the form owns are written, using update rather than set: createdBy,
     * createdAt and status belong to the shift's history, and a set() would wipe them.
     *
     * Registrations are untouched on purpose. People who already signed up stay signed up
     * when a manager fixes a typo or corrects the address -- which is the whole reason to
     * edit rather than delete and re-publish.
     */
    public void updateShift(@NonNull String shiftId, @NonNull Shift shift,
                            @NonNull final Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_TITLE, shift.getTitle());
        data.put(Constants.FIELD_DESCRIPTION, shift.getDescription());
        data.put(Constants.FIELD_DATE, shift.getDate());
        data.put(Constants.FIELD_START_TIME, shift.getStartTime());
        data.put(Constants.FIELD_END_TIME, shift.getEndTime());
        data.put(Constants.FIELD_LOCATION, shift.getLocation());
        data.put(Constants.FIELD_LATITUDE, shift.getLatitude());
        data.put(Constants.FIELD_LONGITUDE, shift.getLongitude());
        data.put(Constants.FIELD_ROLE_REQUIREMENTS, shift.getRoleRequirements());

        db.collection(Constants.COLLECTION_SHIFTS)
                .document(shiftId)
                .update(data)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /**
     * Deletes a shift and every registration attached to it.
     *
     * Deleting the shift on its own would leave orphan registrations behind, which would
     * keep being counted and would show up in an employee's "My shifts" forever.
     */
    public void deleteShift(@NonNull final String shiftId, @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_SHIFT_ID, shiftId)
                .get()
                .addOnSuccessListener(registrationDocs -> {
                    for (QueryDocumentSnapshot registration : registrationDocs) {
                        registration.getReference().delete();
                    }
                    db.collection(Constants.COLLECTION_SHIFTS)
                            .document(shiftId)
                            .delete()
                            .addOnSuccessListener(callback::onSuccess)
                            .addOnFailureListener(callback::onError);
                })
                .addOnFailureListener(callback::onError);
    }

    // -------------------------------------------------------- registrations

    /**
     * Which shifts has this employee applied for?
     *
     * Returns shiftId -> status, which the employee feed hands straight to the adapter
     * so each card can show "Sign up" or "Cancel" correctly. Because this comes from the
     * database and not from memory, a sign-up survives closing the app.
     */
    public void loadMyRegistrationStatuses(@NonNull String employeeId,
                                           @NonNull final Callback<Map<String, String>> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_EMPLOYEE_ID, employeeId)
                .get()
                .addOnSuccessListener(documents -> {
                    Map<String, String> statuses = new HashMap<>();
                    for (QueryDocumentSnapshot document : documents) {
                        String shiftId = document.getString(Constants.FIELD_SHIFT_ID);
                        String status = document.getString(Constants.FIELD_STATUS);
                        if (shiftId != null && status != null
                                && !Constants.REGISTRATION_CANCELLED.equals(status)) {
                            statuses.put(shiftId, status);
                        }
                    }
                    callback.onSuccess(statuses);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * This employee's registrations in ONE business, with the shift attached, for
     * "My shifts".
     *
     * Scoped to the business rather than showing everything they hold everywhere, so the
     * screen matches the business named in the header. Somebody who works for three
     * caterers switches business to see the other two.
     */
    public void loadMyShifts(@NonNull final String employeeId,
                             @NonNull final String businessId,
                             @NonNull final Callback<List<Registration>> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_EMPLOYEE_ID, employeeId)
                .whereEqualTo(Constants.FIELD_BUSINESS_ID, businessId)
                .get()
                .addOnSuccessListener(documents -> {
                    final List<Registration> registrations = new ArrayList<>();
                    for (QueryDocumentSnapshot document : documents) {
                        Registration registration = Registration.fromDocument(document);
                        if (!Constants.REGISTRATION_CANCELLED.equals(registration.getStatus())) {
                            registrations.add(registration);
                        }
                    }
                    attachShiftsTo(registrations, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Looks up the shift behind each registration so "My shifts" can show real details
     * instead of just an id. Registrations whose shift has been deleted are dropped.
     */
    private void attachShiftsTo(final List<Registration> registrations,
                                final Callback<List<Registration>> callback) {
        if (registrations.isEmpty()) {
            callback.onSuccess(registrations);
            return;
        }

        db.collection(Constants.COLLECTION_SHIFTS)
                .get()
                .addOnSuccessListener(shiftDocs -> {
                    Map<String, Shift> shiftsById = new HashMap<>();
                    for (QueryDocumentSnapshot document : shiftDocs) {
                        shiftsById.put(document.getId(), Shift.fromDocument(document));
                    }

                    List<Registration> withShifts = new ArrayList<>();
                    for (Registration registration : registrations) {
                        Shift shift = shiftsById.get(registration.getShiftId());
                        if (shift != null) {
                            registration.setShift(shift);
                            withShifts.add(registration);
                        }
                    }
                    callback.onSuccess(withShifts);
                })
                .addOnFailureListener(callback::onError);
    }

    /** Everyone who applied for one shift, for the manager's approval screen. */
    public void loadApplicants(@NonNull String shiftId,
                               @NonNull final Callback<List<Registration>> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_SHIFT_ID, shiftId)
                .get()
                .addOnSuccessListener(documents -> {
                    List<Registration> applicants = new ArrayList<>();
                    for (QueryDocumentSnapshot document : documents) {
                        Registration registration = Registration.fromDocument(document);
                        if (!Constants.REGISTRATION_CANCELLED.equals(registration.getStatus())) {
                            applicants.add(registration);
                        }
                    }
                    callback.onSuccess(applicants);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * An employee applies for a shift, for one specific role. Starts out "pending".
     *
     * The role is stored on the registration rather than worked out later, because a
     * person who can do two jobs has to say which one they are applying for -- the manager
     * is filling three waiter slots and one cook slot, not four interchangeable bodies.
     */
    public void registerForShift(@NonNull String shiftId,
                                 @NonNull String businessId,
                                 @NonNull String employeeId,
                                 @NonNull String employeeName,
                                 @NonNull String role,
                                 @NonNull final Callback<String> callback) {
        Map<String, Object> data = new HashMap<>();
        data.put(Constants.FIELD_SHIFT_ID, shiftId);
        data.put(Constants.FIELD_BUSINESS_ID, businessId);
        data.put(Constants.FIELD_EMPLOYEE_ID, employeeId);
        data.put(Constants.FIELD_EMPLOYEE_NAME, employeeName);
        data.put(Constants.FIELD_ROLE, role);
        data.put(Constants.FIELD_STATUS, Constants.REGISTRATION_PENDING);
        data.put(Constants.FIELD_NOTE, "");
        data.put(Constants.FIELD_CREATED_AT, FieldValue.serverTimestamp());

        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .add(data)
                .addOnSuccessListener(reference -> callback.onSuccess(reference.getId()))
                .addOnFailureListener(callback::onError);
    }

    /** The employee withdraws. The row is removed outright so the slot frees up. */
    public void cancelRegistration(@NonNull String shiftId,
                                   @NonNull String employeeId,
                                   @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .whereEqualTo(Constants.FIELD_SHIFT_ID, shiftId)
                .whereEqualTo(Constants.FIELD_EMPLOYEE_ID, employeeId)
                .get()
                .addOnSuccessListener(documents -> {
                    for (QueryDocumentSnapshot document : documents) {
                        document.getReference().delete();
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * The employee checks in at the start of their shift -- the app's use of GPS.
     *
     * Both the time and the phone's position are stamped onto the registration. When the
     * shift has a saved position too, the distance between the two is worked out and
     * stored, so the manager can see at a glance whether somebody checked in at the site
     * or from their sofa. Distance is -1 when the shift has no coordinates to compare to,
     * which is honest about not knowing rather than pretending the distance was zero.
     */
    public void checkIn(@NonNull String registrationId,
                        double latitude,
                        double longitude,
                        int distanceMetres,
                        @NonNull final Callback<Void> callback) {
        Map<String, Object> updates = new HashMap<>();
        // The server's clock, not the phone's. Check-in is evidence of attendance, and a
        // device clock can be changed by the person being recorded.
        updates.put(Constants.FIELD_CHECKED_IN_AT, FieldValue.serverTimestamp());
        updates.put(Constants.FIELD_CHECKIN_LATITUDE, latitude);
        updates.put(Constants.FIELD_CHECKIN_LONGITUDE, longitude);
        updates.put(Constants.FIELD_CHECKIN_DISTANCE, distanceMetres);

        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .document(registrationId)
                .update(updates)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /** The manager approves an applicant. */
    public void approveRegistration(@NonNull String registrationId,
                                    @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .document(registrationId)
                .update(Constants.FIELD_STATUS, Constants.REGISTRATION_APPROVED)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    /** The manager removes somebody from a shift, whether approved or still pending. */
    public void removeRegistration(@NonNull String registrationId,
                                   @NonNull final Callback<Void> callback) {
        db.collection(Constants.COLLECTION_REGISTRATIONS)
                .document(registrationId)
                .delete()
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }
}
