package com.example.shiftmanager;

import android.content.Intent;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.List;

/**
 * The two things every signed-in screen needs: a guard, and the header line.
 *
 * Kept in one place because four screens need both, and because getting the guard wrong is
 * a crash rather than a cosmetic bug -- Android can kill the app in the background and
 * bring it back straight to an inner screen, at which point Session is empty and anything
 * reading the current business would hit a null.
 */
public final class SessionUi {

    private SessionUi() {
    }

    /**
     * Call first in every signed-in screen's onCreate, and return immediately if it says
     * false: the screen is already being replaced by the login screen.
     *
     * Being signed in is not enough on its own. The process may have been killed and
     * restarted, which leaves Firebase Auth still holding the account but Session empty,
     * so both are checked and a rebuild is routed through login rather than guessed at.
     */
    public static boolean requireSession(@NonNull AppCompatActivity activity) {
        return require(activity, true);
    }

    /**
     * The same guard for screens that need a signed-in person but no business.
     *
     * The admin screen is the one that needs this: the app's owner belongs to no business,
     * so requireSession would bounce them to the login screen forever.
     */
    public static boolean requireUser(@NonNull AppCompatActivity activity) {
        return require(activity, false);
    }

    private static boolean require(@NonNull AppCompatActivity activity, boolean needsBusiness) {
        boolean signedIn = FirebaseAuth.getInstance().getCurrentUser() != null;
        boolean sessionOk = needsBusiness ? Session.isReady() : Session.hasUser();
        if (signedIn && sessionOk) {
            return true;
        }

        if (!signedIn) {
            Session.clear();
        }

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
        return false;
    }

    /**
     * Fills the line under the app name with the role and the business being viewed, and
     * makes it open the switcher.
     *
     * Roei asked for the role to be visible at all times. Showing the business next to it
     * costs nothing and answers the other half of the same question -- somebody who works
     * for three businesses needs to know which one they are looking at before they read
     * anything else on the screen.
     */
    public static void bindHeader(@NonNull final AppCompatActivity activity,
                                  @NonNull TextView subtitle) {
        AppUser user = Session.getUser();
        Business business = Session.getBusiness();
        if (user == null || business == null) {
            return;
        }

        subtitle.setText(activity.getString(R.string.format_header_role_business,
                user.getRoleLabel(), business.getName()));
        subtitle.setOnClickListener(v -> showBusinessSwitcher(activity));
    }

    /**
     * Lists the businesses this person has been approved for and switches to the one they
     * pick, with a last entry for going off and joining another.
     *
     * Only approved memberships are offered: a business they merely asked to join is not
     * somewhere they can work yet.
     */
    public static void showBusinessSwitcher(@NonNull final AppCompatActivity activity) {
        final AppUser user = Session.getUser();
        if (user == null) {
            return;
        }

        new MembershipRepository().loadMembershipsForUser(user.getId(),
                new Callback<List<Membership>>() {
                    @Override
                    public void onSuccess(List<Membership> memberships) {
                        List<String> approvedIds = new ArrayList<>();
                        for (Membership membership : memberships) {
                            if (membership.isApproved()) {
                                approvedIds.add(membership.getBusinessId());
                            }
                        }
                        loadAndShow(activity, approvedIds);
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(activity, R.string.msg_load_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static void loadAndShow(@NonNull final AppCompatActivity activity,
                                    @NonNull List<String> approvedIds) {
        new BusinessRepository().loadBusinesses(approvedIds, new Callback<List<Business>>() {
            @Override
            public void onSuccess(final List<Business> businesses) {
                if (activity.isFinishing()) {
                    return;
                }

                // The dialog is a plain list of names plus one extra row at the end, so the
                // index of the tapped item is either a business or the "join another" row.
                final String[] labels = new String[businesses.size() + 1];
                for (int i = 0; i < businesses.size(); i++) {
                    labels[i] = businesses.get(i).getName();
                }
                labels[businesses.size()] = activity.getString(R.string.action_join_another);

                new AlertDialog.Builder(activity)
                        .setTitle(R.string.switcher_title)
                        .setItems(labels, (dialog, which) -> {
                            if (which == businesses.size()) {
                                activity.startActivity(new Intent(activity,
                                        BusinessSetupActivity.class));
                                return;
                            }
                            switchTo(activity, businesses.get(which));
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(activity, R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void switchTo(@NonNull final AppCompatActivity activity,
                                 @NonNull final Business business) {
        final AppUser user = Session.getUser();
        if (user == null || business.getId().equals(user.getActiveBusinessId())) {
            return; // already looking at it
        }

        new UserRepository().setActiveBusiness(user.getId(), business.getId(),
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Session.setUser(new AppUser(user.getId(), user.getName(),
                                user.getEmail(), user.getRole(), user.isActive(),
                                business.getId()));
                        Session.setBusiness(business);

                        // Restarting the screen is the simplest correct way to refresh:
                        // every list on it belongs to the old business and has to be
                        // re-read against the new one.
                        activity.recreate();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(activity, R.string.msg_save_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
