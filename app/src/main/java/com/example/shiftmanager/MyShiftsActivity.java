package com.example.shiftmanager;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
 * "My shifts" -- everything the signed-in employee has applied for, approved or not.
 *
 * The feed answers "what could I work?"; this screen answers "what am I actually on?".
 */
public class MyShiftsActivity extends AppCompatActivity
        implements MyShiftsAdapter.OnCheckInListener {

    private final List<Registration> registrations = new ArrayList<>();
    private final ShiftRepository shiftRepository = new ShiftRepository();

    private MyShiftsAdapter adapter;
    private FirebaseAnalytics analytics;
    private LocationHelper locationHelper;

    private ProgressBar progressMyShifts;
    private TextView tvEmptyMyShifts;

    /** Held between asking for location permission and the answer coming back. */
    @Nullable
    private Registration pendingCheckIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_shifts);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("my_shifts_opened", null);

        progressMyShifts = findViewById(R.id.progressMyShifts);
        tvEmptyMyShifts = findViewById(R.id.tvEmptyMyShifts);

        locationHelper = new LocationHelper(this);

        RecyclerView rvMyShifts = findViewById(R.id.rvMyShifts);
        rvMyShifts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyShiftsAdapter(registrations, this);
        rvMyShifts.setAdapter(adapter);

        // "Feed" returns to the shift feed. finish() rather than starting a new
        // EmployeeActivity, so tabbing back and forth cannot stack up copies of it.
        findViewById(R.id.navFeed).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyShifts();
    }

    private void loadMyShifts() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        progressMyShifts.setVisibility(View.VISIBLE);
        tvEmptyMyShifts.setVisibility(View.GONE);

        shiftRepository.loadMyShifts(user.getUid(), new Callback<List<Registration>>() {
            @Override
            public void onSuccess(List<Registration> result) {
                progressMyShifts.setVisibility(View.GONE);

                registrations.clear();
                registrations.addAll(result);
                adapter.notifyDataSetChanged();

                tvEmptyMyShifts.setVisibility(registrations.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                progressMyShifts.setVisibility(View.GONE);
                tvEmptyMyShifts.setVisibility(registrations.isEmpty() ? View.VISIBLE : View.GONE);

                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(MyShiftsActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------------------------------ check-in (GPS)

    /**
     * The employee tapped "Check in" on an approved shift.
     *
     * The registration is remembered while the permission dialog is up, because the answer
     * arrives in a separate callback by which time we would otherwise have forgotten which
     * shift was being checked into.
     */
    @Override
    public void onCheckIn(Registration registration) {
        pendingCheckIn = registration;

        if (!locationHelper.hasPermission()) {
            locationHelper.requestPermission();
            return;
        }
        captureCheckInLocation();
    }

    private void captureCheckInLocation() {
        final Registration registration = pendingCheckIn;
        if (registration == null) {
            return;
        }

        locationHelper.fetchCurrentLocation(new Callback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location == null) {
                    Toast.makeText(MyShiftsActivity.this,
                            R.string.msg_location_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
                saveCheckIn(registration, location);
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(MyShiftsActivity.this,
                        R.string.msg_location_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveCheckIn(Registration registration, Location location) {
        shiftRepository.checkIn(
                registration.getId(),
                location.getLatitude(),
                location.getLongitude(),
                distanceToShift(registration, location),
                new Callback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        analytics.logEvent("shift_checked_in", null);
                        FirebaseCrashlytics.getInstance()
                                .log("Checked in for shift " + registration.getShiftId());
                        Toast.makeText(MyShiftsActivity.this,
                                R.string.msg_checked_in, Toast.LENGTH_SHORT).show();
                        pendingCheckIn = null;
                        loadMyShifts();
                    }

                    @Override
                    public void onError(Exception error) {
                        FirebaseCrashlytics.getInstance().recordException(error);
                        Toast.makeText(MyShiftsActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * How far the employee is from the shift's saved position, in metres.
     *
     * Returns -1 when the shift has no coordinates, so the app records "we do not know"
     * rather than a misleading zero.
     */
    private int distanceToShift(Registration registration, Location here) {
        Shift shift = registration.getShift();
        if (shift == null || !shift.hasCoordinates()) {
            return -1;
        }

        float[] result = new float[1];
        Location.distanceBetween(
                here.getLatitude(), here.getLongitude(),
                shift.getLatitude(), shift.getLongitude(),
                result);
        return Math.round(result[0]);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != LocationHelper.PERMISSION_REQUEST_CODE) {
            return;
        }

        if (LocationHelper.isPermissionGranted(requestCode, grantResults)) {
            captureCheckInLocation();
        } else {
            pendingCheckIn = null;
            Toast.makeText(this, R.string.msg_location_permission_needed, Toast.LENGTH_LONG).show();
        }
    }
}
