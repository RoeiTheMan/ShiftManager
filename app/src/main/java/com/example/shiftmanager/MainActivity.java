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

public class MainActivity extends AppCompatActivity {

    private RecyclerView rvShifts;
    private ShiftAdapter adapter;
    private List<Shift> shiftList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rvShifts = findViewById(R.id.rvShifts);
        rvShifts.setLayoutManager(new LinearLayoutManager(this));

        createMockData();

        adapter = new ShiftAdapter(shiftList, true);
        rvShifts.setAdapter(adapter);

        Button btnSwitchView = findViewById(R.id.btnSwitchView);
        btnSwitchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, EmployeeActivity.class);
                startActivity(intent);
            }
        });

        Button fabAddShift = findViewById(R.id.fabAddShift);
        fabAddShift.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.widget.Toast.makeText(MainActivity.this, "Opening shift creation menu...", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        TextView navTeam = findViewById(R.id.navTeam);
        navTeam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.widget.Toast.makeText(MainActivity.this, "Team screen coming soon!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createMockData() {
        shiftList = new ArrayList<>();
        shiftList.add(new Shift("1", "Morning Shift - Sales", "2026-10-26", "09:00 - 17:00", "Downtown Branch, Floor 2", 2, 1, 3));
        shiftList.add(new Shift("2", "Evening Shift - Support", "2026-10-26", "17:00 - 23:00", "Downtown Branch, Floor 1", 1, 0, 2));
        shiftList.add(new Shift("3", "Morning Shift - Sales", "2026-10-27", "09:00 - 17:00", "Downtown Branch, Floor 2", 3, 0, 3));
        shiftList.add(new Shift("4", "Afternoon Shift - Sales", "2026-10-28", "13:00 - 17:00", "Downtown Branch, Floor 2", 0, 0, 2));
        shiftList.add(new Shift("5", "Night Shift - Security", "2026-10-28", "23:00 - 07:00", "Main Entrance", 1, 0, 1));
        shiftList.add(new Shift("6", "Morning Shift - Support", "2026-10-29", "08:00 - 16:00", "Downtown Branch, Floor 1", 2, 0, 4));
        shiftList.add(new Shift("7", "Weekend Shift - Sales", "2026-10-31", "10:00 - 18:00", "Downtown Branch, Floor 2", 0, 0, 3));
    }
}