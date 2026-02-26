package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class EmployeeActivity extends AppCompatActivity {

    private RecyclerView rvEmployeeShifts;
    private ShiftAdapter adapter;
    private List<Shift> employeeShiftList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_employee);

        rvEmployeeShifts = findViewById(R.id.rvEmployeeShifts);
        rvEmployeeShifts.setLayoutManager(new LinearLayoutManager(this));

        createEmployeeMockData();

        adapter = new ShiftAdapter(employeeShiftList, false);
        rvEmployeeShifts.setAdapter(adapter);

        Button btnBackToManager = findViewById(R.id.btnBackToManager);
        btnBackToManager.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(EmployeeActivity.this, MainActivity.class);
                startActivity(intent);
            }
        });

        TextView navMyShifts = findViewById(R.id.navMyShifts);
        navMyShifts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.widget.Toast.makeText(EmployeeActivity.this, "My Shifts screen coming soon!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createEmployeeMockData() {
        employeeShiftList = new ArrayList<>();
        employeeShiftList.add(new Shift("1", "Morning Shift - Sales", "2026-10-26", "09:00 - 17:00", "Downtown Branch, Floor 2", 2, 1, 3));
        employeeShiftList.add(new Shift("2", "Evening Shift - Support", "2026-10-26", "17:00 - 23:00", "Downtown Branch, Floor 1", 1, 0, 2));
        employeeShiftList.add(new Shift("3", "Morning Shift - Sales", "2026-10-27", "09:00 - 17:00", "Downtown Branch, Floor 2", 3, 0, 3));
        employeeShiftList.add(new Shift("4", "Afternoon Shift - Sales", "2026-10-28", "13:00 - 17:00", "Downtown Branch, Floor 2", 0, 0, 2));
        employeeShiftList.add(new Shift("5", "Night Shift - Security", "2026-10-28", "23:00 - 07:00", "Main Entrance", 1, 0, 1));
        employeeShiftList.add(new Shift("6", "Morning Shift - Support", "2026-10-29", "08:00 - 16:00", "Downtown Branch, Floor 1", 2, 0, 4));
        employeeShiftList.add(new Shift("7", "Weekend Shift - Sales", "2026-10-31", "10:00 - 18:00", "Downtown Branch, Floor 2", 0, 0, 3));
    }
}

