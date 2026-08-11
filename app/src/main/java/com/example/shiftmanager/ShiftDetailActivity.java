package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.List;

/**
 * Shift Detail & Approval -- the screen HW2 describes as the heart of the manager flow.
 *
 * The manager sees everyone who applied for this shift and can approve them or remove
 * them. Approving does not delete anything: it changes the registration's status from
 * "pending" to "approved", which is what makes the counts on the feed move.
 *
 * The same screen opens for an employee, who sees the shift's details but no controls.
 * One screen serving both roles is deliberate -- HW2's whole pitch against Ubeya is that
 * managers and employees live in a single app rather than two.
 */
public class ShiftDetailActivity extends AppCompatActivity
        implements ApplicantAdapter.OnApplicantActionListener {

    private final List<Registration> applicants = new ArrayList<>();
    private final ShiftRepository shiftRepository = new ShiftRepository();
    private final UserRepository userRepository = new UserRepository();

    private ApplicantAdapter adapter;
    private FirebaseAnalytics analytics;

    private TextView tvShiftTitle;
    private TextView tvShiftWhen;
    private TextView tvShiftWhere;
    private TextView tvShiftDescription;
    private TextView tvCapacity;
    private TextView tvEmptyApplicants;
    private ProgressBar progressDetail;
    private RecyclerView rvApplicants;
    private Button btnDeleteShift;
    private Button btnEditShift;

    private String shiftId;
    private Shift shift;

    /** Controls are only shown to a manager, so we have to know the role first. */
    private boolean viewerIsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shift_detail);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        shiftId = getIntent().getStringExtra(Constants.EXTRA_SHIFT_ID);
        if (shiftId == null) {
            // Nothing to show without an id; closing beats displaying an empty screen.
            Toast.makeText(this, R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("shift_detail_opened", null);

        bindViews();
        setUpList();

        btnDeleteShift.setOnClickListener(v -> confirmDeleteShift());

        // Opens the publish form pre-filled. Editing beats deleting and re-publishing,
        // which would throw away everyone who had already signed up.
        btnEditShift.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateShiftActivity.class);
            intent.putExtra(Constants.EXTRA_SHIFT_ID, shiftId);
            startActivity(intent);
        });

        // Back to whichever feed opened this shift. finish() rather than starting a
        // screen, so the caller is returned to instead of a second copy being stacked.
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        loadViewerRole(user.getUid());
    }

    private void bindViews() {
        tvShiftTitle = findViewById(R.id.tvShiftTitle);
        tvShiftWhen = findViewById(R.id.tvShiftWhen);
        tvShiftWhere = findViewById(R.id.tvShiftWhere);
        tvShiftDescription = findViewById(R.id.tvShiftDescription);
        tvCapacity = findViewById(R.id.tvCapacity);
        tvEmptyApplicants = findViewById(R.id.tvEmptyApplicants);
        progressDetail = findViewById(R.id.progressDetail);
        rvApplicants = findViewById(R.id.rvApplicants);
        btnDeleteShift = findViewById(R.id.btnDeleteShift);
        btnEditShift = findViewById(R.id.btnEditShift);
    }

    private void setUpList() {
        rvApplicants.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ApplicantAdapter(applicants, this);
        rvApplicants.setAdapter(adapter);
    }

    /**
     * Looks up whether the person viewing this screen is a manager.
     *
     * If the profile cannot be read we fall back to treating them as an employee, so a
     * failed lookup can never accidentally hand out approval controls.
     */
    private void loadViewerRole(String userId) {
        userRepository.loadUser(userId, new Callback<AppUser>() {
            @Override
            public void onSuccess(AppUser result) {
                viewerIsManager = result.isManager();
                loadShift();
            }

            @Override
            public void onError(Exception error) {
                viewerIsManager = false;
                FirebaseCrashlytics.getInstance().recordException(error);
                loadShift();
            }
        });
    }

    private void loadShift() {
        progressDetail.setVisibility(View.VISIBLE);

        shiftRepository.loadShift(shiftId, new Callback<Shift>() {
            @Override
            public void onSuccess(Shift result) {
                shift = result;
                showShiftDetails();
                loadApplicants();
            }

            @Override
            public void onError(Exception error) {
                progressDetail.setVisibility(View.GONE);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void showShiftDetails() {
        tvShiftTitle.setText(shift.getTitle());

        String when = shift.getDate();
        if (!shift.getTimeRange().isEmpty()) {
            when = when + "  |  " + shift.getTimeRange();
        }
        tvShiftWhen.setText(when);

        tvShiftWhere.setText(shift.getLocation());

        // An empty description would leave a stray blank gap, so hide the row instead.
        tvShiftDescription.setVisibility(
                shift.getDescription().isEmpty() ? View.GONE : View.VISIBLE);
        tvShiftDescription.setText(shift.getDescription());

        btnDeleteShift.setVisibility(viewerIsManager ? View.VISIBLE : View.GONE);
        btnEditShift.setVisibility(viewerIsManager ? View.VISIBLE : View.GONE);
    }

    private void loadApplicants() {
        shiftRepository.loadApplicants(shiftId, new Callback<List<Registration>>() {
            @Override
            public void onSuccess(List<Registration> result) {
                progressDetail.setVisibility(View.GONE);

                applicants.clear();
                applicants.addAll(result);
                adapter.notifyDataSetChanged();

                updateCapacityLine();

                // An employee has no business seeing who else applied, so the list is
                // manager-only; they still get the shift's details above it.
                rvApplicants.setVisibility(viewerIsManager ? View.VISIBLE : View.GONE);
                tvEmptyApplicants.setVisibility(
                        viewerIsManager && applicants.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                progressDetail.setVisibility(View.GONE);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Recounts from the applicant list that is on screen, so the two always agree. */
    private void updateCapacityLine() {
        int approved = 0;
        int pending = 0;
        for (Registration registration : applicants) {
            if (registration.isApproved()) {
                approved++;
            } else if (registration.isPending()) {
                pending++;
            }
        }

        String text = getString(R.string.format_approved, approved, shift.getTotalNeeded());
        if (pending > 0) {
            text = text + "  ·  " + getString(R.string.format_pending, pending);
        }
        tvCapacity.setText(text);
    }

    /** How many approved people this shift already has in one particular role. */
    private int approvedInRole(String role) {
        int count = 0;
        for (Registration other : applicants) {
            if (other.isApproved() && other.getRole().equals(role)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------- manager decisions

    @Override
    public void onApprove(Registration registration) {
        String role = registration.getRole();

        // Capacity is per role now, not one headcount. Three waiters being covered must
        // not block approving the cook, and it must not let a fourth waiter through
        // either -- so the check is against the slot this person actually applied for.
        if (!role.isEmpty()) {
            int needed = shift.getNeededForRole(role);
            if (needed > 0 && approvedInRole(role) >= needed) {
                Toast.makeText(this, R.string.msg_role_full, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // An application from before roles existed: fall back to the shift total.
            int approvedSoFar = 0;
            for (Registration other : applicants) {
                if (other.isApproved()) {
                    approvedSoFar++;
                }
            }
            int total = shift.getTotalNeeded();
            if (total > 0 && approvedSoFar >= total) {
                Toast.makeText(this, R.string.msg_shift_full, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        shiftRepository.approveRegistration(registration.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                analytics.logEvent("registration_approved", null);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_approved, Toast.LENGTH_SHORT).show();
                loadApplicants();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onRemove(Registration registration) {
        shiftRepository.removeRegistration(registration.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                analytics.logEvent("registration_removed", null);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_removed, Toast.LENGTH_SHORT).show();
                loadApplicants();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Deleting a shift throws away other people's sign-ups, so it asks first. */
    private void confirmDeleteShift() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.action_delete_shift)
                .setMessage(R.string.confirm_delete_shift)
                .setPositiveButton(R.string.action_delete_shift, (dialog, which) -> deleteShift())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteShift() {
        shiftRepository.deleteShift(shiftId, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                analytics.logEvent("shift_deleted", null);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_shift_deleted, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(ShiftDetailActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
