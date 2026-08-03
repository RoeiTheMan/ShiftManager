package com.example.shiftmanager;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.Calendar;
import java.util.Locale;

/**
 * The manager's form for publishing a new shift.
 *
 * Date and time are entered through the system pickers rather than typed, so what reaches
 * Firestore is always in the same format ("yyyy-MM-dd" and "HH:mm"). That matters because
 * the feed sorts shifts by the date field, and text sorting only gives the right order
 * when every value is written the same way.
 */
public class CreateShiftActivity extends AppCompatActivity {

    private final ShiftRepository shiftRepository = new ShiftRepository();

    private EditText etTitle;
    private EditText etDescription;
    private EditText etDate;
    private EditText etStartTime;
    private EditText etEndTime;
    private EditText etLocation;
    private EditText etMaxWorkers;
    private TextView tvCapturedLocation;
    private Button btnSaveShift;

    private LocationHelper locationHelper;
    private FirebaseAnalytics analytics;

    /** Set only if the manager taps "use my current location". Stays 0 otherwise. */
    private double capturedLatitude;
    private double capturedLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_shift);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        locationHelper = new LocationHelper(this);

        bindViews();
        setUpPickers();
        setUpButtons();
    }

    private void bindViews() {
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etDate = findViewById(R.id.etDate);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);
        etLocation = findViewById(R.id.etLocation);
        etMaxWorkers = findViewById(R.id.etMaxWorkers);
        tvCapturedLocation = findViewById(R.id.tvCapturedLocation);
        btnSaveShift = findViewById(R.id.btnSaveShift);
    }

    private void setUpPickers() {
        etDate.setOnClickListener(v -> showDatePicker());
        etStartTime.setOnClickListener(v -> showTimePicker(etStartTime));
        etEndTime.setOnClickListener(v -> showTimePicker(etEndTime));
    }

    private void setUpButtons() {
        btnSaveShift.setOnClickListener(v -> saveShift());
        findViewById(R.id.btnUseMyLocation).setOnClickListener(v -> captureLocation());
    }

    // ------------------------------------------------------------ pickers

    private void showDatePicker() {
        Calendar today = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> etDate.setText(
                        // month is 0-based in the picker, so add 1 for a human-readable date
                        String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth)),
                today.get(Calendar.YEAR),
                today.get(Calendar.MONTH),
                today.get(Calendar.DAY_OF_MONTH));

        // A shift in the past would clutter the feed and cannot be worked, so block it.
        dialog.getDatePicker().setMinDate(today.getTimeInMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker(final EditText target) {
        Calendar now = Calendar.getInstance();

        new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> target.setText(
                        String.format(Locale.US, "%02d:%02d", hourOfDay, minute)),
                now.get(Calendar.HOUR_OF_DAY),
                0,
                true // 24-hour clock, so "17:00" is unambiguous
        ).show();
    }

    // ------------------------------------------------------------ GPS

    private void captureLocation() {
        if (!locationHelper.hasPermission()) {
            locationHelper.requestPermission();
            return;
        }
        fetchAndShowLocation();
    }

    private void fetchAndShowLocation() {
        locationHelper.fetchCurrentLocation(new Callback<Location>() {
            @Override
            public void onSuccess(Location location) {
                if (location == null) {
                    Toast.makeText(CreateShiftActivity.this,
                            R.string.msg_location_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }

                capturedLatitude = location.getLatitude();
                capturedLongitude = location.getLongitude();

                tvCapturedLocation.setVisibility(View.VISIBLE);
                tvCapturedLocation.setText(getString(R.string.msg_location_captured)
                        + " " + LocationHelper.describe(location));

                analytics.logEvent("shift_location_captured", null);
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(CreateShiftActivity.this,
                        R.string.msg_location_unavailable, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** The user's answer to the system permission dialog arrives here. */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != LocationHelper.PERMISSION_REQUEST_CODE) {
            return;
        }

        if (LocationHelper.isPermissionGranted(requestCode, grantResults)) {
            fetchAndShowLocation();
        } else {
            Toast.makeText(this, R.string.msg_location_permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ saving

    /**
     * Checks the form, then writes the shift.
     *
     * Validation happens before anything is sent, so a half-filled shift never reaches the
     * database and then shows up in the feed as a blank card.
     */
    private void saveShift() {
        if (!isFormValid()) {
            return;
        }

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        btnSaveShift.setEnabled(false); // stops a double tap creating the shift twice

        Shift shift = new Shift(
                null,
                textOf(etTitle),
                textOf(etDescription),
                textOf(etDate),
                textOf(etStartTime),
                textOf(etEndTime),
                textOf(etLocation),
                capturedLatitude,
                capturedLongitude,
                Integer.parseInt(textOf(etMaxWorkers)),
                Constants.SHIFT_OPEN,
                user.getUid());

        shiftRepository.createShift(shift, new Callback<String>() {
            @Override
            public void onSuccess(String shiftId) {
                analytics.logEvent("shift_created", null);
                FirebaseCrashlytics.getInstance().log("Shift created: " + shiftId);
                Toast.makeText(CreateShiftActivity.this,
                        R.string.msg_shift_created, Toast.LENGTH_SHORT).show();
                finish(); // back to the dashboard, which reloads in onResume
            }

            @Override
            public void onError(Exception error) {
                btnSaveShift.setEnabled(true);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(CreateShiftActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Marks every problem at once rather than making the user fix them one at a time. */
    private boolean isFormValid() {
        boolean valid = true;

        valid &= requireText(etTitle);
        valid &= requireText(etDate);
        valid &= requireText(etStartTime);
        valid &= requireText(etEndTime);
        valid &= requireText(etLocation);

        if (!requireText(etMaxWorkers)) {
            valid = false;
        } else if (parseWorkers(textOf(etMaxWorkers)) < 1) {
            etMaxWorkers.setError(getString(R.string.error_workers_positive));
            valid = false;
        }

        // Times are "HH:mm" on a 24-hour clock, so comparing them as text is enough.
        String start = textOf(etStartTime);
        String end = textOf(etEndTime);
        if (!start.isEmpty() && !end.isEmpty() && end.compareTo(start) <= 0) {
            etEndTime.setError(getString(R.string.error_end_before_start));
            valid = false;
        }

        return valid;
    }

    private boolean requireText(EditText field) {
        if (textOf(field).isEmpty()) {
            field.setError(getString(R.string.error_required));
            return false;
        }
        return true;
    }

    private int parseWorkers(@Nullable String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String textOf(EditText field) {
        return field.getText().toString().trim();
    }
}
