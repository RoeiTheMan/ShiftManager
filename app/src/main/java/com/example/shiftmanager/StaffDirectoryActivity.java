package com.example.shiftmanager;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
     * The manager picks somebody from a list of accounts.
     *
     * This used to be a single email box, which only worked if the manager already knew
     * the address by heart. Anyone opening the app fresh has no way to guess one -- Roei
     * raised it on 2026-08-11 about the lecturer marking the project -- so the accounts
     * are listed up front and the typing only narrows them.
     *
     * People already attached to this business are left out rather than shown and
     * rejected on tap: offering somebody who cannot be added is a dead end.
     */
    private void showInviteDialog() {
        View content = getLayoutInflater().inflate(R.layout.dialog_pick_person, null);
        final EditText etSearch = content.findViewById(R.id.etPersonSearch);
        final RecyclerView rvPeople = content.findViewById(R.id.rvPeople);
        final TextView tvNoPeople = content.findViewById(R.id.tvNoPeople);

        final List<AppUser> shown = new ArrayList<>();
        final List<AppUser> available = new ArrayList<>();

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.action_invite_member)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        PeopleAdapter adapter = new PeopleAdapter(shown, person -> {
            dialog.dismiss();
            checkExistingThenInvite(person);
        });
        rvPeople.setLayoutManager(new LinearLayoutManager(this));
        rvPeople.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Filtered here rather than by re-querying Firestore: the accounts are
                // already loaded, and matching on name as well as email means a manager
                // who only knows somebody's first name can still find them.
                String needle = s.toString().trim().toLowerCase();
                shown.clear();
                for (AppUser person : available) {
                    if (needle.isEmpty()
                            || person.getName().toLowerCase().contains(needle)
                            || person.getEmail().toLowerCase().contains(needle)) {
                        shown.add(person);
                    }
                }
                adapter.notifyDataSetChanged();
                tvNoPeople.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });

        dialog.show();
        loadPeopleToAdd(available, shown, adapter, tvNoPeople);
    }

    /** Every account that is not already attached to this business. */
    private void loadPeopleToAdd(@NonNull final List<AppUser> available,
                                 @NonNull final List<AppUser> shown,
                                 @NonNull final PeopleAdapter adapter,
                                 @NonNull final TextView tvNoPeople) {
        final Set<String> alreadyHere = new HashSet<>();
        for (Membership membership : memberships) {
            alreadyHere.add(membership.getUserId());
        }

        userRepository.loadAllUsers(new Callback<List<AppUser>>() {
            @Override
            public void onSuccess(List<AppUser> users) {
                available.clear();
                for (AppUser person : users) {
                    if (!alreadyHere.contains(person.getId())) {
                        available.add(person);
                    }
                }
                Collections.sort(available,
                        (a, b) -> a.getName().compareToIgnoreCase(b.getName()));

                shown.clear();
                shown.addAll(available);
                adapter.notifyDataSetChanged();
                tvNoPeople.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
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
