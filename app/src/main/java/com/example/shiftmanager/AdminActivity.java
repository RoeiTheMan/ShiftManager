package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The app owner's overview: every business in the app, and everyone inside each one.
 *
 * This is the answer to the first thing that was wrong with the old app -- "I don't know
 * what users I have in the software". Accounts existed only in the Firebase Console, and
 * nothing in the app itself listed them.
 *
 * Unlike every other signed-in screen this one is NOT scoped to a business. The admin
 * does not belong to one, so it needs a signed-in user and nothing else, and it reads the
 * whole of all three collections rather than filtering by a business id.
 *
 * The accounts belonging to no business at all get their own group at the bottom. Without
 * it, somebody who registered and never joined anywhere would be invisible here -- which
 * is precisely the case this screen exists to make visible.
 */
public class AdminActivity extends AppCompatActivity implements AdminAdapter.OnAdminRowListener {

    private final BusinessRepository businessRepository = new BusinessRepository();
    private final UserRepository userRepository = new UserRepository();
    private final MembershipRepository membershipRepository = new MembershipRepository();

    private final List<AdminRow> rows = new ArrayList<>();
    private AdminAdapter adapter;
    private FirebaseAnalytics analytics;

    private RecyclerView rvAdmin;
    private ProgressBar progressAdmin;
    private TextView tvEmptyAdmin, tvAdminSummary;

    /**
     * Which businesses are currently opened up. Held on the screen rather than on the
     * rows, so rebuilding the list after a reload does not fold everything shut again.
     */
    private final Set<String> expandedGroupIds = new HashSet<>();

    /** The last thing each read handed back, kept so the rows can be rebuilt on a tap. */
    private List<Business> businesses = new ArrayList<>();
    private List<AppUser> users = new ArrayList<>();
    private List<Membership> memberships = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        // Only a signed-in user is required, not a business: the admin has none.
        if (!SessionUi.requireUser(this)) {
            return;
        }

        // Belt and braces. Nothing routes a non-admin here, but this screen shows every
        // account in the app, so it should refuse rather than trust that.
        if (!Session.getUser().isAdmin()) {
            finish();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("admin_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Admin screen opened");

        rvAdmin = findViewById(R.id.rvAdmin);
        progressAdmin = findViewById(R.id.progressAdmin);
        tvEmptyAdmin = findViewById(R.id.tvEmptyAdmin);
        tvAdminSummary = findViewById(R.id.tvAdminSummary);

        rvAdmin.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter(rows, this);
        rvAdmin.setAdapter(adapter);

        Button btnLogout = findViewById(R.id.btnAdminLogout);
        btnLogout.setOnClickListener(v -> logout());
    }

    /** Reloaded on every return, so a business created elsewhere shows up straight away. */
    @Override
    protected void onResume() {
        super.onResume();
        if (Session.hasUser()) {
            loadEverything();
        }
    }

    // ---------- Loading ----------

    /**
     * Reads the three collections one after another, then draws the list once.
     *
     * Sequential rather than all at once on purpose: three reads that each depend on
     * nothing would need counting and a shared result holder to know when the last one
     * landed, and the extra complexity buys a fraction of a second on lists this size.
     */
    private void loadEverything() {
        setLoading(true);

        businessRepository.loadAllBusinesses(new Callback<List<Business>>() {
            @Override
            public void onSuccess(List<Business> loadedBusinesses) {
                businesses = loadedBusinesses;
                loadUsers();
            }

            @Override
            public void onError(Exception e) {
                failed(e);
            }
        });
    }

    private void loadUsers() {
        userRepository.loadAllUsers(new Callback<List<AppUser>>() {
            @Override
            public void onSuccess(List<AppUser> loadedUsers) {
                users = loadedUsers;
                loadMemberships();
            }

            @Override
            public void onError(Exception e) {
                failed(e);
            }
        });
    }

    private void loadMemberships() {
        membershipRepository.loadAllMemberships(new Callback<List<Membership>>() {
            @Override
            public void onSuccess(List<Membership> loadedMemberships) {
                memberships = loadedMemberships;
                setLoading(false);
                rebuildRows();
            }

            @Override
            public void onError(Exception e) {
                failed(e);
            }
        });
    }

    private void failed(@NonNull Exception e) {
        setLoading(false);
        FirebaseCrashlytics.getInstance().recordException(e);
        Toast.makeText(this, R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
    }

    // ---------- Building the list ----------

    /**
     * Turns the three lists into the flat list of rows the adapter draws.
     *
     * Called again on every expand and collapse, because opening a business means its
     * people appear in the list and closing it means they are left out.
     */
    private void rebuildRows() {
        rows.clear();

        Map<String, AppUser> usersById = new HashMap<>();
        for (AppUser user : users) {
            usersById.put(user.getId(), user);
        }

        // Group the memberships by business, and note everyone who turned up in one, so
        // the leftover accounts can be worked out afterwards.
        Map<String, List<Membership>> byBusiness = new HashMap<>();
        Set<String> placedUserIds = new HashSet<>();
        for (Membership membership : memberships) {
            List<Membership> group = byBusiness.get(membership.getBusinessId());
            if (group == null) {
                group = new ArrayList<>();
                byBusiness.put(membership.getBusinessId(), group);
            }
            group.add(membership);
            placedUserIds.add(membership.getUserId());
        }

        for (Business business : businesses) {
            List<Membership> people = byBusiness.get(business.getId());
            if (people == null) {
                people = new ArrayList<>();
            }

            // Same order as the Team screen: anything still waiting on an answer sits at
            // the top, so what is outstanding is visible without opening every business.
            Collections.sort(people, (a, b) -> {
                int aFirst = a.isPending() ? 0 : 1;
                int bFirst = b.isPending() ? 0 : 1;
                if (aFirst != bFirst) {
                    return aFirst - bFirst;
                }
                return a.getUserName().compareToIgnoreCase(b.getUserName());
            });

            boolean expanded = expandedGroupIds.contains(business.getId());
            rows.add(AdminRow.group(business.getId(), business.getName(),
                    business.getSubtitle(), people.size(), expanded));

            if (expanded) {
                for (Membership membership : people) {
                    rows.add(AdminRow.person(resolvePerson(usersById, membership),
                            membership.getStatusLabel()));
                }
            }
        }

        addAccountsWithoutABusiness(placedUserIds);

        adapter.notifyDataSetChanged();
        tvAdminSummary.setText(getString(R.string.format_admin_summary,
                getResources().getQuantityString(R.plurals.admin_business_count,
                        businesses.size(), businesses.size()),
                getResources().getQuantityString(R.plurals.admin_account_count,
                        users.size(), users.size())));
        tvEmptyAdmin.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * The person behind a membership.
     *
     * Normally their real account, looked up by id. If that account is gone -- deleted
     * from the Firebase Console, say -- the membership still carries the name, email and
     * role that were copied onto it when it was created, so a stand-in is built from
     * those. Showing the row with slightly older details beats dropping a person out of
     * the business they are still recorded as being in.
     */
    @NonNull
    private AppUser resolvePerson(@NonNull Map<String, AppUser> usersById,
                                  @NonNull Membership membership) {
        AppUser known = usersById.get(membership.getUserId());
        if (known != null) {
            return known;
        }
        return new AppUser(membership.getUserId(), membership.getUserName(),
                membership.getUserEmail(), membership.getUserRole(), true, "");
    }

    /** The accounts that are in no business at all, as one more group at the bottom. */
    private void addAccountsWithoutABusiness(@NonNull Set<String> placedUserIds) {
        List<AppUser> orphans = new ArrayList<>();
        for (AppUser user : users) {
            if (!placedUserIds.contains(user.getId())) {
                orphans.add(user);
            }
        }
        if (orphans.isEmpty()) {
            return;
        }

        Collections.sort(orphans, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        // An empty group id: this group has no business document behind it, but it
        // expands and collapses through exactly the same code as the real ones.
        boolean expanded = expandedGroupIds.contains("");
        rows.add(AdminRow.group("", getString(R.string.admin_no_business_group), "",
                orphans.size(), expanded));

        if (expanded) {
            for (AppUser orphan : orphans) {
                rows.add(AdminRow.person(orphan, getString(R.string.admin_status_no_business)));
            }
        }
    }

    // ---------- Tapping ----------

    @Override
    public void onGroupToggled(@NonNull AdminRow row) {
        if (!expandedGroupIds.remove(row.getGroupId())) {
            expandedGroupIds.add(row.getGroupId());
        }
        rebuildRows();
    }

    /**
     * What is actually knowable about an account.
     *
     * Roei asked to see people's passwords here. That is not possible for anyone,
     * including the owner of the app: Firebase Auth stores a salted hash of the password
     * and never gives the original back -- there is no setting, no API and no admin
     * override for it. The dialog says so plainly rather than leaving the gap unexplained,
     * and offers the thing that actually solves the underlying problem: sending the person
     * a password-reset email so they can set a new one themselves.
     */
    @Override
    public void onPersonClicked(@NonNull final AppUser person) {
        String details = getString(R.string.admin_person_details,
                person.getEmail().isEmpty() ? "—" : person.getEmail(),
                person.getRole(),
                person.getCreatedLabel());

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(person.getName())
                .setMessage(details)
                .setNegativeButton(R.string.action_close, null)
                .setNeutralButton(R.string.action_delete_profile,
                        (dialog, which) -> confirmDeleteProfile(person));

        // A reset can only be sent somewhere, so the button is only offered when there is
        // an address to send it to.
        if (!person.getEmail().isEmpty()) {
            builder.setPositiveButton(R.string.action_send_password_reset,
                    (dialog, which) -> sendPasswordReset(person.getEmail()));
        }

        builder.show();
    }

    /**
     * Deleting a profile is how the admin clears out duplicates.
     *
     * They accumulate on their own: removing a login in the Firebase console leaves its
     * profile behind, and signing up again with the same address writes a second one. Four
     * profiles for one email is the state this button exists to clean up.
     */
    private void confirmDeleteProfile(@NonNull final AppUser person) {
        new AlertDialog.Builder(this)
                .setTitle(person.getName())
                .setMessage(R.string.confirm_delete_profile)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete_profile,
                        (dialog, which) -> deleteProfile(person))
                .show();
    }

    private void deleteProfile(@NonNull AppUser person) {
        // Deleting your own profile would leave you signed in with nothing behind you, and
        // the next screen to read the session would find a person who no longer exists.
        if (Session.getUser() != null && person.getId().equals(Session.getUser().getId())) {
            Toast.makeText(this, R.string.msg_cannot_delete_self, Toast.LENGTH_LONG).show();
            return;
        }

        userRepository.deleteProfile(person.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                analytics.logEvent("admin_profile_deleted", null);
                Toast.makeText(AdminActivity.this,
                        R.string.msg_profile_deleted, Toast.LENGTH_SHORT).show();
                loadEverything();
            }

            @Override
            public void onError(Exception e) {
                FirebaseCrashlytics.getInstance().recordException(e);
                Toast.makeText(AdminActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendPasswordReset(@NonNull String email) {
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> {
                    analytics.logEvent("admin_password_reset_sent", null);
                    Toast.makeText(AdminActivity.this,
                            R.string.msg_password_reset_sent, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    // The usual reason is an account that only ever signed in with Google
                    // and so has no password to reset. Saying so beats a silent failure.
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(AdminActivity.this,
                            R.string.msg_password_reset_failed, Toast.LENGTH_LONG).show();
                });
    }

    // ---------- Plumbing ----------

    private void logout() {
        analytics.logEvent("logout_clicked", null);
        FirebaseAuth.getInstance().signOut();
        Session.clear();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressAdmin.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvEmptyAdmin.setVisibility(View.GONE);
        }
    }
}
