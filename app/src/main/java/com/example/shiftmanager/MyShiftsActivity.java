package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

/**
 * "My shifts" -- everything the signed-in employee has applied for, approved or not.
 *
 * The feed answers "what could I work?"; this screen answers "what am I actually on?".
 */
public class MyShiftsActivity extends AppCompatActivity {

    private final List<Registration> registrations = new ArrayList<>();
    private final ShiftRepository shiftRepository = new ShiftRepository();

    private MyShiftsAdapter adapter;
    private FirebaseAnalytics analytics;

    private ProgressBar progressMyShifts;
    private TextView tvEmptyMyShifts;

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

        RecyclerView rvMyShifts = findViewById(R.id.rvMyShifts);
        rvMyShifts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyShiftsAdapter(registrations);
        rvMyShifts.setAdapter(adapter);
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
}
