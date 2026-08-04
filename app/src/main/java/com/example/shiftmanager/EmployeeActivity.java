package com.example.shiftmanager;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
    private final UserRepository userRepository = new UserRepository();

    private ShiftAdapter adapter;
    private FirebaseAnalytics analytics;
    private LocationHelper locationHelper;

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

        loadEmployeeName(user.getUid());
        locationHelper = new LocationHelper(this);

        bindViews();
        setUpList();
        setUpButtons();
        requestLocationForDistances();
    }

    /**
     * Asks for location so each card can show how far away the shift is.
     *
     * This is the side of the GPS feature the employee actually benefits from: deciding
     * whether a shift is worth travelling to. A refusal is not an error -- the distance
     * line just stays empty and everything else still works.
     */
    private void requestLocationForDistances() {
        if (!locationHelper.hasPermission()) {
            locationHelper.requestPermission();
            return;
        }
        loadCurrentLocation();
    }

    private void loadCurrentLocation() {
        locationHelper.fetchCurrentLocation(new Callback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location != null) {
                    adapter.setCurrentLocation(location);
                }
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().log("Employee feed could not read location");
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (LocationHelper.isPermissionGranted(requestCode, grantResults)) {
            loadCurrentLocation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loadShifts();
        }
    }

    /**
     * Loads this employee's name from their profile in the users collection.
     *
     * It deliberately does NOT use the Firebase Auth display name. Google sign-ins have
     * one, but email/password sign-ups do not, so that route fell back to showing the raw
     * email address -- which then appeared as the applicant's "name" on the manager's
     * approval screen. LoginActivity already saves a proper name when the profile is
     * created, so reading it back keeps one source of truth for what a person is called.
     */
    private void loadEmployeeName(String userId) {
        userRepository.loadUser(userId, new Callback<AppUser>() {
            @Override
            public void onSuccess(AppUser user) {
                employeeName = user.getName();
            }

            @Override
            public void onError(Exception error) {
                // Fall back to the email prefix, the same shape LoginActivity would save.
                FirebaseUser current = FirebaseAuth.getInstance().getCurrentUser();
                String email = current != null && current.getEmail() != null
                        ? current.getEmail() : "";
                employeeName = email.contains("@")
                        ? email.substring(0, email.indexOf('@'))
                        : "Employee";
            }
        });
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
