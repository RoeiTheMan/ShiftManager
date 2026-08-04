package com.example.shiftmanager;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Team screen: everyone attached to this business, and the requests waiting on a
 * manager to answer.
 *
 * This is the manager's side of both join directions. Somebody who searched for the
 * business and asked to join shows up here with an Approve button; somebody the manager
 * adds by email is recorded as pending and waits for THAT PERSON to accept, which they do
 * on their own business screen. Nobody is ever put into a business one-sidedly.
 */
public class StaffDirectoryActivity extends AppCompatActivity
        implements StaffAdapter.OnStaffActionListener {

    private final List<Membership> memberships = new ArrayList<>();
    private final MembershipRepository membershipRepository = new MembershipRepository();
    private final UserRepository userRepository = new UserRepository();

    private StaffAdapter adapter;
    private FirebaseAnalytics analytics;

    private RecyclerView rvStaff;
    private ProgressBar progressStaff;
    private TextView tvEmptyStaff;

    /** Managers (and the admin) can approve, invite and remove. Employees only look. */
    private boolean canManage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_directory);

        if (!SessionUi.requireSession(this)) {
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("team_screen_opened", null);

        AppUser user = Session.getUser();
        canManage = user.isManager() || user.isAdmin();

        rvStaff = findViewById(R.id.rvStaff);
        progressStaff = findViewById(R.id.progressStaff);
        tvEmptyStaff = findViewById(R.id.tvEmptyStaff);

        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StaffAdapter(memberships, canManage, this);
        rvStaff.setAdapter(adapter);

        View fab = findViewById(R.id.fabAddEmployee);
        fab.setVisibility(canManage ? View.VISIBLE : View.GONE);
        fab.setOnClickListener(v -> showInviteDialog());

        SessionUi.bindHeader(this, findViewById(R.id.tvHeaderSubtitle));

        // "Shifts" returns to the dashboard. finish() rather than starting a new
        // MainActivity, so tabbing back and forth cannot stack up copies of the screen.
        findViewById(R.id.navShifts).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Session.isReady()) {
            loadTeam();
        }
    }

    // ---------- Loading ----------

    private void loadTeam() {
        progressStaff.setVisibility(View.VISIBLE);
        tvEmptyStaff.setVisibility(View.GONE);

        membershipRepository.loadMembershipsForBusiness(Session.getBusiness().getId(),
                new Callback<List<Membership>>() {
                    @Override
                    public void onSuccess(List<Membership> result) {
                        progressStaff.setVisibility(View.GONE);

                        memberships.clear();
                        memberships.addAll(result);

                        // Anything needing the manager's answer goes to the top, so a join
                        // request is not buried under a long list of existing staff.
                        Collections.sort(memberships, (a, b) -> {
                            int aFirst = a.isWaitingOnManager() ? 0 : 1;
                            int bFirst = b.isWaitingOnManager() ? 0 : 1;
                            if (aFirst != bFirst) {
                                return aFirst - bFirst;
                            }
                            return a.getUserName().compareToIgnoreCase(b.getUserName());
                        });

                        adapter.notifyDataSetChanged();
                        tvEmptyStaff.setVisibility(memberships.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(Exception error) {
                        progressStaff.setVisibility(View.GONE);
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------- Inviting somebody (join flow 4) ----------

    /**
     * The manager adds somebody by email address.
     *
     * Only an email is asked for, because the person's name already exists on their own
     * profile -- typing a second copy here would give the app two names for one person and
     * no way to know which is right.
     */
    private void showInviteDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final EditText etEmail = new EditText(this);
        etEmail.setHint(R.string.hint_employee_email);
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        container.addView(etEmail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.action_invite_member)
                .setMessage(R.string.invite_help)
                .setView(container)
                .setPositiveButton(R.string.action_invite, null) // wired below
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        // Wired after show() so a bad address keeps the dialog open instead of closing it
        // and throwing the manager's typing away.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                etEmail.setError(getString(R.string.error_required));
                return;
            }
            dialog.dismiss();
            inviteByEmail(email);
        });
    }

    private void inviteByEmail(@NonNull final String email) {
        progressStaff.setVisibility(View.VISIBLE);

        userRepository.findUserByEmail(email, new Callback<AppUser>() {
            @Override
            public void onSuccess(AppUser found) {
                if (found == null) {
                    // There is no account to invite. Saying so plainly beats creating a
                    // half-person record that can never sign in.
                    progressStaff.setVisibility(View.GONE);
                    Toast.makeText(StaffDirectoryActivity.this,
                            R.string.msg_no_account_for_email, Toast.LENGTH_LONG).show();
                    return;
                }
                checkExistingThenInvite(found);
            }

            @Override
            public void onError(Exception error) {
                progressStaff.setVisibility(View.GONE);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Stops the same person being invited twice, or invited when they already belong. */
    private void checkExistingThenInvite(@NonNull final AppUser person) {
        membershipRepository.findMembership(Session.getBusiness().getId(), person.getId(),
                new Callback<Membership>() {
                    @Override
                    public void onSuccess(Membership existing) {
                        if (existing != null) {
                            progressStaff.setVisibility(View.GONE);
                            Toast.makeText(StaffDirectoryActivity.this,
                                    existing.isApproved()
                                            ? R.string.msg_already_member
                                            : R.string.msg_already_pending,
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        sendInvite(person);
                    }

                    @Override
                    public void onError(Exception error) {
                        progressStaff.setVisibility(View.GONE);
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendInvite(@NonNull AppUser person) {
        membershipRepository.createMembership(Session.getBusiness().getId(), person,
                Constants.REQUESTED_BY_MANAGER, Constants.MEMBERSHIP_PENDING,
                new Callback<String>() {
                    @Override
                    public void onSuccess(String membershipId) {
                        analytics.logEvent("member_invited", null);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_invite_sent, Toast.LENGTH_LONG).show();
                        loadTeam();
                    }

                    @Override
                    public void onError(Exception error) {
                        progressStaff.setVisibility(View.GONE);
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---------- Answering a request (join flow 3) ----------

    @Override
    public void onApproveMember(final Membership membership) {
        membershipRepository.setStatus(membership.getId(), Constants.MEMBERSHIP_APPROVED,
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        analytics.logEvent("member_approved", null);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_approved, Toast.LENGTH_SHORT).show();
                        loadTeam();
                    }

                    @Override
                    public void onError(Exception error) {
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /** Removing somebody also drops their sign-ups here, so it asks first. */
    @Override
    public void onRemoveMember(final Membership membership) {
        new AlertDialog.Builder(this)
                .setTitle(membership.getUserName())
                .setMessage(R.string.confirm_remove_employee)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> removeMember(membership))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void removeMember(final Membership membership) {
        membershipRepository.removeMembership(membership.getId(), membership.getBusinessId(),
                membership.getUserId(), new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        analytics.logEvent("member_removed", null);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_employee_removed, Toast.LENGTH_SHORT).show();
                        loadTeam();
                    }

                    @Override
                    public void onError(Exception error) {
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(StaffDirectoryActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
