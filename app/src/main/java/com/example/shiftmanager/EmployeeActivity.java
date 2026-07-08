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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class EmployeeActivity extends AppCompatActivity {

    private RecyclerView rvEmployeeShifts;
    private ShiftAdapter adapter;
    private List<Shift> employeeShiftList;
    private FirebaseFirestore db;
    private FirebaseAnalytics analytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee);

        // Guard: this screen requires a signed-in user
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("employee_screen_opened", null);

        rvEmployeeShifts = findViewById(R.id.rvEmployeeShifts);
        rvEmployeeShifts.setLayoutManager(new LinearLayoutManager(this));

        employeeShiftList = new ArrayList<>();
        adapter = new ShiftAdapter(employeeShiftList, false);
        rvEmployeeShifts.setAdapter(adapter);

        loadShiftsFromFirestore();

        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analytics.logEvent("logout_clicked", null);
                FirebaseCrashlytics.getInstance().log("Logout clicked (employee)");

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(EmployeeActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            }
        });

        TextView navMyShifts = findViewById(R.id.navMyShifts);
        navMyShifts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analytics.logEvent("my_shifts_tab_clicked", null);

                Toast.makeText(EmployeeActivity.this, "My Shifts screen coming soon!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadShiftsFromFirestore() {
        db.collection("shifts")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    employeeShiftList.clear();

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

                        employeeShiftList.add(shift);
                    }

                    adapter.notifyDataSetChanged();
                    analytics.logEvent("employee_firestore_shifts_loaded", null);
                    Toast.makeText(EmployeeActivity.this, "Loaded shifts from Firestore", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    analytics.logEvent("employee_firestore_load_failed", null);
                    Toast.makeText(EmployeeActivity.this, "Failed to load Firestore data", Toast.LENGTH_SHORT).show();
                });
    }
}

