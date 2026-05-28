package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvShifts;
    private ShiftAdapter adapter;
    private List<Shift> shiftList;
    private FirebaseFirestore db;
    private FirebaseAnalytics analytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        analytics = FirebaseAnalytics.getInstance(this);

        analytics.logEvent("manager_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Manager screen opened");

        rvShifts = findViewById(R.id.rvShifts);
        rvShifts.setLayoutManager(new LinearLayoutManager(this));

        shiftList = new ArrayList<>();
        adapter = new ShiftAdapter(shiftList, true);
        rvShifts.setAdapter(adapter);

        loadShiftsFromFirestore();

        Button btnSwitchView = findViewById(R.id.btnSwitchView);
        btnSwitchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analytics.logEvent("employee_view_clicked", null);
                FirebaseCrashlytics.getInstance().log("Employee View button clicked");

                Intent intent = new Intent(MainActivity.this, EmployeeActivity.class);
                startActivity(intent);
            }
        });

        Button fabAddShift = findViewById(R.id.fabAddShift);
        fabAddShift.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analytics.logEvent("add_shift_clicked", null);
                FirebaseCrashlytics.getInstance().log("Add Shift button clicked");

                Toast.makeText(MainActivity.this, "Opening shift creation menu...", Toast.LENGTH_SHORT).show();
            }
        });

        TextView navTeam = findViewById(R.id.navTeam);
        navTeam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analytics.logEvent("team_tab_clicked", null);
                FirebaseCrashlytics.getInstance().log("Team tab clicked");

                Toast.makeText(MainActivity.this, "Team screen coming soon!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadShiftsFromFirestore() {
        db.collection("shifts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    shiftList.clear();

                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        String id = document.getId();
                        String title = document.getString("title");

                        Shift shift = new Shift(
                                id,
                                title != null ? title : "Untitled Shift",
                                "2026-10-26",
                                "09:00 - 17:00",
                                "Downtown Branch",
                                0,
                                0,
                                3
                        );

                        shiftList.add(shift);
                    }

                    adapter.notifyDataSetChanged();
                    analytics.logEvent("firestore_shifts_loaded", null);
                    FirebaseCrashlytics.getInstance().log("Firestore shifts loaded successfully");

                    Toast.makeText(MainActivity.this, "Loaded shifts from Firestore", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    analytics.logEvent("firestore_load_failed", null);
                    FirebaseCrashlytics.getInstance().recordException(e);

                    Toast.makeText(MainActivity.this, "Failed to load Firestore data", Toast.LENGTH_SHORT).show();
                });
    }
}