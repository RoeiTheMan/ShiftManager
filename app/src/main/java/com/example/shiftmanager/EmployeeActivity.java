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

        // Needs a signed-in user AND a loaded business, since Android can reopen this
        // screen after killing the app, leaving the account signed in but Session empty.
        if (!SessionUi.requireSession(this)) {
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("employee_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Employee screen opened");

        // The name is already in the session from sign-in, so there is no second read and
        // no window where a registration could be stamped with a half-loaded name.
        employeeName = Session.getUser().getName();
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
        // Session, not just auth: onCreate may already have sent us back to login.
        if (Session.isReady()) {
            loadShifts();
        }
    }

    private void bindViews() {
        rvShifts = findViewById(R.id.rvEmployeeShifts);
        progressShifts = findViewById(R.id.progressShifts);
        tvEmptyShifts = findViewById(R.id.tvEmptyShifts);

        // "Employee · Golden Events", and tapping it switches business.
        SessionUi.bindHeader(this, findViewById(R.id.tvHeaderSubtitle));
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
            // Otherwise the next person to sign in would briefly see the old business.
            Session.clear();
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

        shiftRepository.loadShiftsWithCounts(Session.getBusiness().getId(),
                new Callback<List<Shift>>() {
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

    /**
     * Signing up starts by asking which role they are applying for.
     *
     * The shift wants a specific mix -- three waiters and one cook, say -- so an
     * application has to name a slot. The employee sees WHICH roles are wanted but not how
     * many of each; that half is the manager's to know.
     */
    private void signUpFor(final Shift shift, final String employeeId) {
        analytics.logEvent("shift_signup_clicked", null);

        final List<String> roles = new ArrayList<>(shift.getRoleRequirements().keySet());
        if (roles.isEmpty()) {
            // A shift published before roles existed, or one asking for nobody. There is
            // nothing to pick, so record the application with no role rather than blocking.
            submitSignUp(shift, employeeId, "");
            return;
        }
        if (roles.size() == 1) {
            submitSignUp(shift, employeeId, roles.get(0)); // no point asking
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.shift_pick_role_title)
                .setItems(roles.toArray(new String[0]), (dialog, which) ->
                        submitSignUp(shift, employeeId, roles.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submitSignUp(Shift shift, String employeeId, String role) {
        shiftRepository.registerForShift(shift.getId(), Session.getBusiness().getId(),
                employeeId, employeeName, role,
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
