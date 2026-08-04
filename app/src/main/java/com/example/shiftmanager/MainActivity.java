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
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.List;

/**
 * The manager's dashboard: every shift in the business, oldest first, with how many
 * workers are approved and still pending on each one.
 *
 * Tapping a card opens the shift's detail and approval screen. The + button opens the
 * form for publishing a new shift. Nothing on this screen is hardcoded -- all of it is
 * read from Firestore through ShiftRepository.
 */
public class MainActivity extends AppCompatActivity implements ShiftAdapter.OnShiftClickListener {

    private final List<Shift> shifts = new ArrayList<>();
    private final ShiftRepository shiftRepository = new ShiftRepository();

    private ShiftAdapter adapter;
    private FirebaseAnalytics analytics;
    private LocationHelper locationHelper;

    private RecyclerView rvShifts;
    private ProgressBar progressShifts;
    private TextView tvEmptyShifts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Needs a signed-in user AND a loaded business. Both are checked because Android
        // can kill the app in the background and reopen it straight onto this screen,
        // which leaves the account signed in but the session empty.
        if (!SessionUi.requireSession(this)) {
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("manager_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Manager screen opened");

        locationHelper = new LocationHelper(this);

        bindViews();
        setUpList();
        setUpButtons();
        requestLocationForDistances();
    }

    /**
     * Asks for location so each card can show how far away the shift is.
     *
     * The feed works perfectly well without it, so a refusal is not treated as an error:
     * the distance line simply stays empty. Permission is only asked for once per launch.
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
                // Distances are a nicety here; the feed itself is unaffected.
                FirebaseCrashlytics.getInstance().log("Manager feed could not read location");
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

    /**
     * Reloads on every return to the screen, so publishing a shift or approving somebody
     * is reflected the moment the user comes back instead of needing a manual refresh.
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Session, not just auth: onCreate may have already sent us to the login screen,
        // and reloading against a business that is not there would crash on the way out.
        if (Session.isReady()) {
            loadShifts();
        }
    }

    private void bindViews() {
        rvShifts = findViewById(R.id.rvShifts);
        progressShifts = findViewById(R.id.progressShifts);
        tvEmptyShifts = findViewById(R.id.tvEmptyShifts);

        // "Manager · Golden Events", and tapping it switches business.
        SessionUi.bindHeader(this, findViewById(R.id.tvHeaderSubtitle));
    }

    private void setUpList() {
        rvShifts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ShiftAdapter(shifts, true, this);
        rvShifts.setAdapter(adapter);
    }

    private void setUpButtons() {
        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            analytics.logEvent("logout_clicked", null);
            FirebaseCrashlytics.getInstance().log("Logout clicked (manager)");
            FirebaseAuth.getInstance().signOut();
            // Without this the next person to sign in on this device would briefly see the
            // previous user's business in the header.
            Session.clear();
            goToLogin();
        });

        Button fabAddShift = findViewById(R.id.fabAddShift);
        fabAddShift.setOnClickListener(v -> {
            analytics.logEvent("add_shift_clicked", null);
            startActivity(new Intent(this, CreateShiftActivity.class));
        });

        TextView navTeam = findViewById(R.id.navTeam);
        navTeam.setOnClickListener(v -> {
            analytics.logEvent("team_tab_clicked", null);
            startActivity(new Intent(this, StaffDirectoryActivity.class));
        });
    }

    private void loadShifts() {
        showLoading(true);

        shiftRepository.loadShiftsWithCounts(new Callback<List<Shift>>() {
            @Override
            public void onSuccess(List<Shift> result) {
                showLoading(false);

                shifts.clear();
                shifts.addAll(result);
                adapter.notifyDataSetChanged();

                tvEmptyShifts.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);

                analytics.logEvent("firestore_shifts_loaded", null);
                FirebaseCrashlytics.getInstance().log("Manager loaded " + shifts.size() + " shifts");
            }

            @Override
            public void onError(Exception error) {
                showLoading(false);
                tvEmptyShifts.setVisibility(shifts.isEmpty() ? View.VISIBLE : View.GONE);

                analytics.logEvent("firestore_load_failed", null);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(MainActivity.this, R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean loading) {
        progressShifts.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvEmptyShifts.setVisibility(View.GONE);
        }
    }

    /** Tapping the card opens the approval screen for that shift. */
    @Override
    public void onShiftClicked(Shift shift) {
        openShiftDetail(shift);
    }

    /** The manager's card button does the same thing, since "Manage" is the only action. */
    @Override
    public void onShiftActionClicked(Shift shift) {
        openShiftDetail(shift);
    }

    private void openShiftDetail(Shift shift) {
        analytics.logEvent("manage_shift_clicked", null);
        Intent intent = new Intent(this, ShiftDetailActivity.class);
        intent.putExtra(Constants.EXTRA_SHIFT_ID, shift.getId());
        startActivity(intent);
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
