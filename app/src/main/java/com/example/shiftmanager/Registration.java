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
    private final String employeeId;
    private final String employeeName;
    private final String status;
    private final String note;

    /** Filled in after loading, so a screen can show the shift behind this registration. */
    @Nullable
    private Shift shift;

    public Registration(String id, String shiftId, String employeeId,
                        String employeeName, String status, String note) {
        this.id = id;
        this.shiftId = shiftId;
        this.employeeId = employeeId;
        this.employeeName = employeeName;
        this.status = status;
        this.note = note;
    }

    @NonNull
    public static Registration fromDocument(@NonNull DocumentSnapshot document) {
        return new Registration(
                document.getId(),
                textOr(document, Constants.FIELD_SHIFT_ID, ""),
                textOr(document, Constants.FIELD_EMPLOYEE_ID, ""),
                textOr(document, Constants.FIELD_EMPLOYEE_NAME, "Unnamed employee"),
                textOr(document, Constants.FIELD_STATUS, Constants.REGISTRATION_PENDING),
                textOr(document, Constants.FIELD_NOTE, "")
        );
    }

    private static String textOr(DocumentSnapshot document, String field, String fallback) {
        String value = document.getString(field);
        return value != null ? value : fallback;
    }

    public String getId() { return id; }
    public String getShiftId() { return shiftId; }
    public String getEmployeeId() { return employeeId; }
    public String getEmployeeName() { return employeeName; }
    public String getStatus() { return status; }
    public String getNote() { return note; }

    @Nullable
    public Shift getShift() { return shift; }

    public void setShift(@Nullable Shift shift) { this.shift = shift; }

    public boolean isApproved() {
        return Constants.REGISTRATION_APPROVED.equals(status);
    }

    public boolean isPending() {
        return Constants.REGISTRATION_PENDING.equals(status);
    }
}
