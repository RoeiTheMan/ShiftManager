package com.example.shiftmanager;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

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
    private LinearLayout containerRoleRequirements;
    private TextView tvCapturedLocation;
    private Button btnSaveShift;

    /** The "how many" field for each staff role, keyed by the role's name. */
    private final Map<String, EditText> roleCountFields = new LinkedHashMap<>();

    private LocationHelper locationHelper;
    private FirebaseAnalytics analytics;

    /** Set only if the manager taps "use my current location". Stays 0 otherwise. */
    private double capturedLatitude;
    private double capturedLongitude;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_shift);

        if (!SessionUi.requireSession(this)) {
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        locationHelper = new LocationHelper(this);

        bindViews();
        buildRoleRows();
        setUpPickers();
        setUpButtons();

        // A visible way out. The system back gesture already worked, but it is not
        // discoverable, so the form read as a screen you could only leave by saving.
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void bindViews() {
        etTitle = findViewById(R.id.etTitle);
        etDescription = findViewById(R.id.etDescription);
        etDate = findViewById(R.id.etDate);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);
        etLocation = findViewById(R.id.etLocation);
        containerRoleRequirements = findViewById(R.id.containerRoleRequirements);
        tvCapturedLocation = findViewById(R.id.tvCapturedLocation);
        btnSaveShift = findViewById(R.id.btnSaveShift);
    }

    /**
     * Adds one "role + how many" row for each staff role this business defined.
     *
     * Built at runtime rather than written into the layout, because the roles are different
     * for every business -- a caterer has waiters and cooks, a shop has cashiers. Each row's
     * number field is kept in roleCountFields so saving can read them back by role name.
     */
    private void buildRoleRows() {
        List<String> staffRoles = Session.getBusiness().getStaffRoles();

        for (String role : staffRoles) {
            View row = getLayoutInflater().inflate(
                    R.layout.role_requirement_row, containerRoleRequirements, false);

            ((TextView) row.findViewById(R.id.tvRoleName)).setText(role);
            EditText countField = row.findViewById(R.id.etRoleCount);

            roleCountFields.put(role, countField);
            containerRoleRequirements.addView(row);
        }
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
                Session.getBusiness().getId(),
                textOf(etTitle),
                textOf(etDescription),
                textOf(etDate),
                textOf(etStartTime),
                textOf(etEndTime),
                textOf(etLocation),
                capturedLatitude,
                capturedLongitude,
                collectRoleRequirements(),
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
        valid &= hasAtLeastOneRole();

        // Times are "HH:mm" on a 24-hour clock, so comparing them as text is enough.
        String start = textOf(etStartTime);
        String end = textOf(etEndTime);
        if (!start.isEmpty() && !end.isEmpty() && end.compareTo(start) <= 0) {
            etEndTime.setError(getString(R.string.error_end_before_start));
            valid = false;
        }

        return valid;
    }

    /**
     * Reads the per-role numbers off the form.
     *
     * Roles left blank or set to zero are dropped rather than stored as 0, so a shift's
     * role list only ever names jobs it is actually looking for. A shift that needs two
     * waiters and no cook should not advertise a cook slot nobody can fill.
     */
    private Map<String, Integer> collectRoleRequirements() {
        Map<String, Integer> requirements = new TreeMap<>();
        for (Map.Entry<String, EditText> entry : roleCountFields.entrySet()) {
            int count = parseWorkers(textOf(entry.getValue()));
            if (count > 0) {
                requirements.put(entry.getKey(), count);
            }
        }
        return requirements;
    }

    /**
     * A shift has to ask for somebody. Without this, a manager could publish a shift that
     * needs nobody, which no employee could ever sign up for.
     */
    private boolean hasAtLeastOneRole() {
        if (!collectRoleRequirements().isEmpty()) {
            return true;
        }

        if (!roleCountFields.isEmpty()) {
            EditText firstField = roleCountFields.values().iterator().next();
            firstField.setError(getString(R.string.error_need_one_staffed_role));
            firstField.requestFocus();
        } else {
            // The business was created without any staff roles, so there is nothing to
            // staff. Say so plainly instead of silently refusing to save.
            Toast.makeText(this, R.string.error_business_has_no_roles, Toast.LENGTH_LONG).show();
        }
        return false;
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
