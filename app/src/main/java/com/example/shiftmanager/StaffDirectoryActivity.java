package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.ArrayList;
import java.util.List;

/**
 * The Staff Directory: everyone who works for the business.
 *
 * The list comes from the same "users" collection that LoginActivity writes to when
 * somebody signs in for the first time, so anyone who has used the app already appears
 * here without being entered twice. The + button covers the other case -- a member of
 * staff the manager wants on the roster before they have ever opened the app.
 */
public class StaffDirectoryActivity extends AppCompatActivity
        implements StaffAdapter.OnStaffActionListener {

    private final List<AppUser> employees = new ArrayList<>();
    private final UserRepository userRepository = new UserRepository();

    private StaffAdapter adapter;
    private FirebaseAnalytics analytics;

    private RecyclerView rvStaff;
    private ProgressBar progressStaff;
    private TextView tvEmptyStaff;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_directory);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        analytics = FirebaseAnalytics.getInstance(this);
        analytics.logEvent("staff_directory_opened", null);

        rvStaff = findViewById(R.id.rvStaff);
        progressStaff = findViewById(R.id.progressStaff);
        tvEmptyStaff = findViewById(R.id.tvEmptyStaff);

        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StaffAdapter(employees, this);
        rvStaff.setAdapter(adapter);

        findViewById(R.id.fabAddEmployee).setOnClickListener(v -> showAddEmployeeDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEmployees();
    }

    private void loadEmployees() {
        progressStaff.setVisibility(View.VISIBLE);
        tvEmptyStaff.setVisibility(View.GONE);

        userRepository.loadEmployees(new Callback<List<AppUser>>() {
            @Override
            public void onSuccess(List<AppUser> result) {
                progressStaff.setVisibility(View.GONE);

                employees.clear();
                employees.addAll(result);
                adapter.notifyDataSetChanged();

                tvEmptyStaff.setVisibility(employees.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(Exception error) {
                progressStaff.setVisibility(View.GONE);
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * A small two-field dialog rather than a whole screen.
     *
     * Adding a member of staff needs a name and an email and nothing else, so a separate
     * Activity would be more navigation than the task deserves.
     */
    private void showAddEmployeeDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final EditText etName = new EditText(this);
        etName.setHint(R.string.hint_employee_name);
        etName.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        container.addView(etName);

        final EditText etEmail = new EditText(this);
        etEmail.setHint(R.string.hint_employee_email);
        etEmail.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        container.addView(etEmail);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.action_add_employee)
                .setView(container)
                .setPositiveButton(R.string.action_add_employee, null) // wired below
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        // The click is wired after show() so an empty name can keep the dialog open
        // instead of it closing and throwing the manager's typing away.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();

            if (name.isEmpty()) {
                etName.setError(getString(R.string.error_required));
                return;
            }

            addEmployee(name, email);
            dialog.dismiss();
        });
    }

    private void addEmployee(String name, String email) {
        userRepository.addEmployee(name, email, new Callback<String>() {
            @Override
            public void onSuccess(String employeeId) {
                analytics.logEvent("employee_added", null);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_employee_added, Toast.LENGTH_SHORT).show();
                loadEmployees();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Removing somebody also drops their sign-ups, so it asks first. */
    @Override
    public void onRemoveEmployee(AppUser employee) {
        new AlertDialog.Builder(this)
                .setTitle(employee.getName())
                .setMessage(R.string.confirm_remove_employee)
                .setPositiveButton(R.string.action_remove, (dialog, which) -> removeEmployee(employee))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void removeEmployee(AppUser employee) {
        userRepository.removeEmployee(employee.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                analytics.logEvent("employee_removed", null);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_employee_removed, Toast.LENGTH_SHORT).show();
                loadEmployees();
            }

            @Override
            public void onError(Exception error) {
                FirebaseCrashlytics.getInstance().recordException(error);
                Toast.makeText(StaffDirectoryActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
