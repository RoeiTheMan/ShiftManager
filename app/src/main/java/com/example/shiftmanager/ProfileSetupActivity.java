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
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * The first screen a brand new account sees.
 *
 * Registration used to be an email, a password and a one-tap role dialog, which meant the
 * app never learned the person's name and they landed in an empty screen belonging to
 * nothing. This asks for the name, asks what they actually do, and then hands them
 * straight to the business step -- so by the time they reach a shift feed, they are a
 * named person inside a named business.
 */
public class ProfileSetupActivity extends AppCompatActivity {

    private final UserRepository userRepository = new UserRepository();

    private EditText etFullName;
    private Button btnIAmManager, btnIAmEmployee, btnContinueAdmin;
    private TextView tvRoleQuestion;
    private ProgressBar progressProfile;

    private FirebaseUser authUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_setup);

        authUser = FirebaseAuth.getInstance().getCurrentUser();
        if (authUser == null) {
            // Nothing to set up a profile for. Can happen if the process was killed
            // between signing in and this screen opening.
            goToLogin();
            return;
        }

        etFullName = findViewById(R.id.etFullName);
        btnIAmManager = findViewById(R.id.btnIAmManager);
        btnIAmEmployee = findViewById(R.id.btnIAmEmployee);
        btnContinueAdmin = findViewById(R.id.btnContinueAdmin);
        tvRoleQuestion = findViewById(R.id.tvRoleQuestion);
        progressProfile = findViewById(R.id.progressProfile);

        // Google sign-in already knows the person's name, so pre-fill it rather than
        // making them type something the app was just handed.
        String suggestedName = authUser.getDisplayName();
        if (suggestedName != null && !suggestedName.isEmpty()) {
            etFullName.setText(suggestedName);
        }

        String email = authUser.getEmail() != null ? authUser.getEmail() : "";

        // The app owner does not get asked what their role is -- it is decided by the
        // address they signed in with, so showing the question would imply a choice
        // that does not exist.
        if (Constants.ADMIN_EMAIL.equalsIgnoreCase(email)) {
            tvRoleQuestion.setText(R.string.profile_admin_recognised);
            btnIAmManager.setVisibility(View.GONE);
            btnIAmEmployee.setVisibility(View.GONE);
            btnContinueAdmin.setVisibility(View.VISIBLE);
            btnContinueAdmin.setOnClickListener(v -> saveProfile(Constants.ROLE_ADMIN));
        } else {
            btnContinueAdmin.setVisibility(View.GONE);
            btnIAmManager.setOnClickListener(v -> saveProfile(Constants.ROLE_MANAGER));
            btnIAmEmployee.setOnClickListener(v -> saveProfile(Constants.ROLE_EMPLOYEE));
        }
    }

    private void saveProfile(@NonNull String role) {
        final String name = etFullName.getText().toString().trim();
        if (name.isEmpty()) {
            etFullName.setError(getString(R.string.error_required));
            etFullName.requestFocus();
            return;
        }

        String email = authUser.getEmail() != null ? authUser.getEmail() : "";

        setLoading(true);
        userRepository.createProfile(authUser.getUid(), name, email, role,
                new Callback<AppUser>() {
                    @Override
                    public void onSuccess(AppUser createdUser) {
                        FirebaseAnalytics.getInstance(ProfileSetupActivity.this)
                                .logEvent("profile_created_" + createdUser.getRole(), null);

                        Session.setUser(createdUser);

                        // Straight on to picking a business -- a profile on its own still
                        // belongs to nothing, which was the original complaint. The admin
                        // is the exception: they oversee every business rather than
                        // working in one, so there is nothing for them to pick.
                        startActivity(new Intent(ProfileSetupActivity.this,
                                createdUser.isAdmin()
                                        ? AdminActivity.class
                                        : BusinessSetupActivity.class));
                        finish();
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        Toast.makeText(ProfileSetupActivity.this,
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
        progressProfile.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnIAmManager.setEnabled(!loading);
        btnIAmEmployee.setEnabled(!loading);
        btnContinueAdmin.setEnabled(!loading);
        etFullName.setEnabled(!loading);
    }
}
