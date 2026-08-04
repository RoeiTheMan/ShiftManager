package com.example.shiftmanager;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentSnapshot;

/**
 * The link between one person and one business, from the "businessMemberships" collection.
 *
 * A separate collection rather than a field on the user, because a person can work for
 * several businesses at once -- event staff usually do -- and because joining is a
 * request that somebody else has to answer, so the link needs its own status.
 *
 * The same document covers both join directions. An employee who asks to join is waiting
 * on a manager; an employee a manager added is waiting on the employee. requestedBy is
 * what tells the two apart, so a screen can ask "is this mine to answer?".
 */
public class Membership {

    private final String id;
    private final String businessId;
    private final String userId;
    private final String userName;
    private final String userEmail;
    private final String userRole;
    private final String status;
    private final String requestedBy;

    public Membership(String id, String businessId, String userId, String userName,
                      String userEmail, String userRole, String status, String requestedBy) {
        this.id = id;
        this.businessId = businessId;
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userRole = userRole;
        this.status = status;
        this.requestedBy = requestedBy;
    }

    @NonNull
    public static Membership fromDocument(@NonNull DocumentSnapshot document) {
        String businessId = document.getString(Constants.FIELD_BUSINESS_ID);
        String userId = document.getString(Constants.FIELD_USER_ID);
        String userName = document.getString(Constants.FIELD_USER_NAME);
        String userEmail = document.getString(Constants.FIELD_USER_EMAIL);
        String userRole = document.getString(Constants.FIELD_ROLE);
        String status = document.getString(Constants.FIELD_STATUS);
        String requestedBy = document.getString(Constants.FIELD_REQUESTED_BY);

        return new Membership(
                document.getId(),
                businessId != null ? businessId : "",
                userId != null ? userId : "",
                userName != null ? userName : "Unnamed",
                userEmail != null ? userEmail : "",
                userRole != null ? userRole : Constants.ROLE_EMPLOYEE,
                status != null ? status : Constants.MEMBERSHIP_PENDING,
                requestedBy != null ? requestedBy : Constants.REQUESTED_BY_JOINER
        );
    }

    public String getId() { return id; }
    public String getBusinessId() { return businessId; }
    public String getUserId() { return userId; }
    public String getUserName() { return userName; }
    public String getUserEmail() { return userEmail; }
    public String getUserRole() { return userRole; }
    public String getStatus() { return status; }
    public String getRequestedBy() { return requestedBy; }

    public boolean isApproved() {
        return Constants.MEMBERSHIP_APPROVED.equals(status);
    }

    public boolean isPending() {
        return Constants.MEMBERSHIP_PENDING.equals(status);
    }

    /** True when this request is still waiting on a manager to answer it. */
    public boolean isWaitingOnManager() {
        return isPending() && Constants.REQUESTED_BY_JOINER.equals(requestedBy);
    }

    /** True when this request is still waiting on the invited employee to answer it. */
    public boolean isWaitingOnEmployee() {
        return isPending() && Constants.REQUESTED_BY_MANAGER.equals(requestedBy);
    }

    /** The status line shown next to a person's name in the team list. */
    public String getStatusLabel() {
        if (isApproved()) {
            return "Member";
        }
        if (isWaitingOnManager()) {
            return "Asked to join";
        }
        if (isWaitingOnEmployee()) {
            return "Invited, waiting for them";
        }
        return "Declined";
    }
}
