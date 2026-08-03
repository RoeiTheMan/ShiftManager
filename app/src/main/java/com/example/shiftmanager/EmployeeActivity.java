package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The employee's shift feed: every available shift, oldest first, with a button to sign
 * up or to withdraw.
 *
 * Whether the button says "Sign up" or "Cancel" comes from the shiftRegistrations
 * collection rather than from memory, so a sign-up made yesterday is still shown today.
 */
public class EmployeeActivity extends AppCompatActivity implements ShiftAdapter.OnShiftClickListener {

    private final List<Shift> shifts = new ArrayList<>();
    private final ShiftRepository shiftRepository = new ShiftRepository();

    private ShiftAdapter adapter;
    private FirebaseAnalytics analytics;

    private RecyclerView rvShifts;
    private ProgressBar progressShifts;
    private TextView tvEmptyShifts;

    /** The signed-in employee's display name, saved onto each registration they make. */
    private String employeeName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            goToLogin();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("employee_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Employee screen opened");

        employeeName = displayNameOf(user);

        bindViews();
        setUpList();
        setUpButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loadShifts();
        }
    }

    /** Google gives us a name; email/password sign-ups often do not, so fall back. */
    private String displayNameOf(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            return user.getDisplayName();
        }
        if (user.getEmail() != null) {
            return user.getEmail();
        }
        return "Employee";
    }

    private void bindViews() {
        rvShifts = findViewById(R.id.rvEmployeeShifts);
        progressShifts = findViewById(R.id.progressShifts);
        tvEmptyShifts = findViewById(R.id.tvEmptyShifts);
    }

    private void setUpList() {
        rvShifts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShiftAdapter(shifts, false, this);
        rvShifts.setAdapter(adapter);
    }

    private void setUpButtons() {
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            analytics.logEvent("logout_clicked", null);
            FirebaseCrashlytics.getInstance().log("Logout clicked (employee)");
            FirebaseAuth.getInstance().signOut();
            goToLogin();
        });

        TextView navMyShifts = findViewById(R.id.navMyShifts);
        navMyShifts.setOnClickListener(v -> {
            analytics.logEvent("my_shifts_tab_clicked", null);
            startActivity(new Intent(this, MyShiftsActivity.class));
        });
    }

    private void loadShifts() {
        showLoading(true);

        shiftRepository.loadShiftsWithCounts(new Callback<List<Shift>>() {
            @Override
            public void onSuccess(List<Shift> result) {
                shifts.clear();
                shifts.addAll(result);
                adapter.notifyDataSetChanged();

                // Second read: which of these has this employee already applied for?
                loadMyRegistrationStatuses();
            }

            @Override
            public void onError(Exception error) {
                showLoading(false);
                tvEmptyShifts.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);

                analytics.logEvent("employee_firestore_load_failed", null);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(EmployeeActivity.this, R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadMyRegistrationStatuses() {
        String employeeId = currentUserId();
        if (employeeId == null) {
            finishLoading();
            return;
        }

        shiftRepository.loadMyRegistrationStatuses(employeeId, new Callback<Map<String, String>>() {
            @Override
            public void onSuccess(Map<String, String> statuses) {
                adapter.setRegistrationStatuses(statuses);
                finishLoading();

                analytics.logEvent("employee_firestore_shifts_loaded", null);
                FirebaseCrashlytics.getInstance()
                        .log("Employee loaded " + shifts.size() + " shifts");
            }

            @Override
            public void onError(Exception error) {
                // The feed itself is fine; we just cannot tell which ones they applied for.
                FirebaseCrashlytics.getInstance().recordException(error);
                finishLoading();
            }
        });
    }

    private void finishLoading() {
        showLoading(false);
        tvEmptyShifts.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showLoading(boolean loading) {
        progressShifts.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvEmptyShifts.setVisibility(View.GONE);
        }
    }

    /** Tapping the card opens the shift's details in read-only form. */
    @Override
    public void onShiftClicked(Shift shift) {
        Intent intent = new Intent(this, ShiftDetailActivity.class);
        intent.putExtra(Constants.EXTRA_SHIFT_ID, shift.getId());
        startActivity(intent);
    }

    /** The button signs the employee up, or withdraws them if they already applied. */
    @Override
    public void onShiftActionClicked(Shift shift) {
        String employeeId = currentUserId();
        if (employeeId == null) {
            goToLogin();
            return;
        }

        if (adapter.isRegisteredFor(shift.getId())) {
            cancelRegistration(shift, employeeId);
        } else {
            signUpFor(shift, employeeId);
        }
    }

    private void signUpFor(Shift shift, String employeeId) {
        analytics.logEvent("shift_signup_clicked", null);

        shiftRepository.registerForShift(shift.getId(), employeeId, employeeName,
                new Callback<String>() {
                    @Override
                    public void onSuccess(String registrationId) {
                        Toast.makeText(EmployeeActivity.this,
                                R.string.msg_signed_up, Toast.LENGTH_SHORT).show();
                        loadShifts();
                    }

                    @Override
                    public void onError(Exception error) {
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(EmployeeActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void cancelRegistration(Shift shift, String employeeId) {
        analytics.logEvent("shift_cancel_clicked", null);

        shiftRepository.cancelRegistration(shift.getId(), employeeId, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Toast.makeText(EmployeeActivity.this,
                        R.string.msg_registration_cancelled, Toast.LENGTH_SHORT).show();
                loadShifts();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(EmployeeActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String currentUserId() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
