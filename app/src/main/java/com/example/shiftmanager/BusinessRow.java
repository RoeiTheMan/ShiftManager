package com.example.shiftmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One line in the business list: a business, plus where the signed-in user stands with it.
 *
 * The two are kept together because the row has to show both -- the name and address come
 * from the business, while whether it says "Member", "Asked to join" or "Tap to request"
 * depends on a membership that may not exist yet. The adapter should not have to look
 * anything up, so the screen resolves both sides before handing rows over.
 */
public class BusinessRow {

    private final Business business;
    private final Membership membership;

    public BusinessRow(@NonNull Business business, @Nullable Membership membership) {
        this.business = business;
        this.membership = membership;
    }

    @NonNull
    public Business getBusiness() {
        return business;
    }

    /** Null when the user has no link to this business at all. */
    @Nullable
    public Membership getMembership() {
        return membership;
    }

    public boolean isMember() {
        return membership != null && membership.isApproved();
    }

    /** True when this row is an invitation the user still has to accept or decline. */
    public boolean isInvitation() {
        return membership != null && membership.isWaitingOnEmployee();
    }

    /** True when the user asked to join and is still waiting on a manager. */
    public boolean isAwaitingApproval() {
        return membership != null && membership.isWaitingOnManager();
    }
}
