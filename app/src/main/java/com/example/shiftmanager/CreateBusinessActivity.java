package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A manager setting up a brand new business (join flow 1).
 *
 * Besides the business's own details this is where its staff roles are defined -- waiter,
 * cook, bartender and so on. They are collected once here and then picked from every time
 * a shift is staffed, which is why a business cannot be created without at least one.
 *
 * The creator is made a manager of the business immediately and without approval: there is
 * nobody else in the business yet who could approve them.
 */
public class CreateBusinessActivity extends AppCompatActivity {

    private final BusinessRepository businessRepository = new BusinessRepository();
    private final MembershipRepository membershipRepository = new MembershipRepository();
    private final UserRepository userRepository = new UserRepository();

    private EditText etBusinessName, etBusinessType, etBusinessAddress,
            etBusinessDescription, etNewRole;
    private Button btnAddRole, btnRemoveLastRole, btnSaveBusiness;
    private TextView tvRolesList;
    private ProgressBar progressBusiness;

    /** The staff roles added so far. Written to the business document on save. */
    private final List<String> staffRoles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_business);

        if (!Session.hasUser()) {
            goToLogin();
            return;
        }

        etBusinessName = findViewById(R.id.etBusinessName);
        etBusinessType = findViewById(R.id.etBusinessType);
        etBusinessAddress = findViewById(R.id.etBusinessAddress);
        etBusinessDescription = findViewById(R.id.etBusinessDescription);
        etNewRole = findViewById(R.id.etNewRole);
        btnAddRole = findViewById(R.id.btnAddRole);
        btnRemoveLastRole = findViewById(R.id.btnRemoveLastRole);
        btnSaveBusiness = findViewById(R.id.btnSaveBusiness);
        tvRolesList = findViewById(R.id.tvRolesList);
        progressBusiness = findViewById(R.id.progressBusiness);

        btnAddRole.setOnClickListener(v -> addRole());
        btnRemoveLastRole.setOnClickListener(v -> removeLastRole());
        btnSaveBusiness.setOnClickListener(v -> saveBusiness());

        renderRoles();
    }

    // ---------- Staff roles ----------

    private void addRole() {
        String role = etNewRole.getText().toString().trim();
        if (role.isEmpty()) {
            etNewRole.setError(getString(R.string.error_required));
            return;
        }

        // Compared lower-cased so "Waiter" cannot be added a second time as "waiter" --
        // two spellings of one role would show up as two separate lines when staffing.
        String comparable = role.toLowerCase(Locale.ROOT);
        for (String existing : staffRoles) {
            if (existing.toLowerCase(Locale.ROOT).equals(comparable)) {
                etNewRole.setError(getString(R.string.error_role_duplicate));
                return;
            }
        }

        staffRoles.add(role);
        etNewRole.setText("");
        etNewRole.setError(null);
        renderRoles();
    }

    private void removeLastRole() {
        if (!staffRoles.isEmpty()) {
            staffRoles.remove(staffRoles.size() - 1);
            renderRoles();
        }
    }

    private void renderRoles() {
        if (staffRoles.isEmpty()) {
            tvRolesList.setText(R.string.business_no_roles_yet);
            btnRemoveLastRole.setVisibility(View.GONE);
            return;
        }

        StringBuilder text = new StringBuilder();
        for (int i = 0; i < staffRoles.size(); i++) {
            if (i > 0) {
                text.append("  ·  ");
            }
            text.append(staffRoles.get(i));
        }
        tvRolesList.setText(text.toString());
        btnRemoveLastRole.setVisibility(View.VISIBLE);
    }

    // ---------- Saving ----------

    private void saveBusiness() {
        final String name = etBusinessName.getText().toString().trim();
        String type = etBusinessType.getText().toString().trim();
        String address = etBusinessAddress.getText().toString().trim();
        String description = etBusinessDescription.getText().toString().trim();

        if (name.isEmpty()) {
            etBusinessName.setError(getString(R.string.error_required));
            etBusinessName.requestFocus();
            return;
        }
        if (staffRoles.isEmpty()) {
            // Without roles the manager could publish a shift that asks for nobody.
            etNewRole.setError(getString(R.string.error_need_one_role));
            etNewRole.requestFocus();
            return;
        }

        final AppUser user = Session.getUser();
        setLoading(true);

        businessRepository.createBusiness(name, type, address, description, staffRoles,
                user.getId(), new Callback<String>() {
                    @Override
                    public void onSuccess(String businessId) {
                        makeCreatorAManager(businessId, name);
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        Toast.makeText(CreateBusinessActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Links the creator to the business they just made, already approved.
     *
     * If this step failed the business would exist with nobody in it and the creator would
     * have no way back to it, so a failure here is reported rather than ignored.
     */
    private void makeCreatorAManager(@NonNull final String businessId,
                                     @NonNull final String businessName) {
        final AppUser user = Session.getUser();

        membershipRepository.createMembership(businessId, user,
                Constants.REQUESTED_BY_MANAGER, Constants.MEMBERSHIP_APPROVED,
                new Callback<String>() {
                    @Override
                    public void onSuccess(String membershipId) {
                        openNewBusiness(businessId, businessName);
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        Toast.makeText(CreateBusinessActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openNewBusiness(@NonNull final String businessId,
                                 @NonNull final String businessName) {
        final AppUser user = Session.getUser();

        userRepository.setActiveBusiness(user.getId(), businessId, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                FirebaseAnalytics.getInstance(CreateBusinessActivity.this)
                        .logEvent("business_created", null);

                // Rebuild the session so the header immediately shows the new business
                // instead of whatever was there before.
                Session.setUser(new AppUser(user.getId(), user.getName(), user.getEmail(),
                        user.getRole(), user.isActive(), businessId));
                Session.setBusiness(new Business(businessId, businessName,
                        etBusinessType.getText().toString().trim(),
                        etBusinessAddress.getText().toString().trim(),
                        etBusinessDescription.getText().toString().trim(),
                        staffRoles, user.getId()));

                Toast.makeText(CreateBusinessActivity.this,
                        R.string.msg_business_created, Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(CreateBusinessActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                Toast.makeText(CreateBusinessActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void goToLogin() {
        Session.clear();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void setLoading(boolean loading) {
        progressBusiness.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSaveBusiness.setEnabled(!loading);
        btnAddRole.setEnabled(!loading);
    }
}
