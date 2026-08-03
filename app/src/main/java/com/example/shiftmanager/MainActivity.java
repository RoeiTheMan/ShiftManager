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

    private RecyclerView rvShifts;
    private ProgressBar progressShifts;
    private TextView tvEmptyShifts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // This screen requires a signed-in user; bounce back to login if there is none.
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            goToLogin();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("manager_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Manager screen opened");

        bindViews();
        setUpList();
        setUpButtons();
    }

    /**
     * Reloads on every return to the screen, so publishing a shift or approving somebody
     * is reflected the moment the user comes back instead of needing a manual refresh.
     */
    @Override
    protected void onResume() {
        super.onResume();
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            loadShifts();
        }
    }

    private void bindViews() {
        rvShifts = findViewById(R.id.rvShifts);
        progressShifts = findViewById(R.id.progressShifts);
        tvEmptyShifts = findViewById(R.id.tvEmptyShifts);
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
