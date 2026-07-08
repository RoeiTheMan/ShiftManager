package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseAnalytics analytics;
    private GoogleSignInClient googleSignInClient;

    private EditText etEmail, etPassword;
    private Button btnLogin, btnRegister, btnGoogleSignIn;
    private ProgressBar progressLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        analytics = FirebaseAnalytics.getInstance(this);

        analytics.logEvent("login_screen_opened", null);
        FirebaseCrashlytics.getInstance().log("Login screen opened");

        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressLogin = findViewById(R.id.progressLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginWithEmail();
            }
        });

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                registerWithEmail();
            }
        });

        btnGoogleSignIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGoogleSignIn();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // If a user is already signed in, skip the login form and route them
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            routeUser(currentUser);
        }
    }

    // ---------- Email / password ----------

    private void loginWithEmail() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter email and password", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    analytics.logEvent("email_login_success", null);
                    FirebaseCrashlytics.getInstance().log("Email login success");
                    routeUser(result.getUser());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    analytics.logEvent("email_login_failed", null);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this, "Login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void registerWithEmail() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Enter an email and a password (6+ characters) to register", Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    analytics.logEvent("email_register_success", null);
                    FirebaseCrashlytics.getInstance().log("Email registration success");
                    routeUser(result.getUser());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    analytics.logEvent("email_register_failed", null);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ---------- Google sign-in ----------

    private void startGoogleSignIn() {
        analytics.logEvent("google_signin_clicked", null);

        // The web client id is generated from google-services.json once Google sign-in
        // is enabled in the Firebase Console. Look it up at runtime so the app still
        // builds and runs before that setup step is done.
        int webClientIdRes = getResources().getIdentifier("default_web_client_id", "string", getPackageName());
        if (webClientIdRes == 0) {
            Toast.makeText(this, "Google sign-in is not configured yet (see Firebase Console setup)", Toast.LENGTH_LONG).show();
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(webClientIdRes))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        startActivityForResult(googleSignInClient.getSignInIntent(), RC_GOOGLE_SIGN_IN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RC_GOOGLE_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            try {
                GoogleSignInAccount account = task.getResult(ApiException.class);
                firebaseAuthWithGoogle(account);
            } catch (ApiException e) {
                analytics.logEvent("google_signin_failed", null);
                FirebaseCrashlytics.getInstance().recordException(e);
                Toast.makeText(this, "Google sign-in failed (code " + e.getStatusCode() + ")", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        setLoading(true);
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> {
                    analytics.logEvent("google_login_success", null);
                    FirebaseCrashlytics.getInstance().log("Google login success");
                    routeUser(result.getUser());
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    analytics.logEvent("google_login_failed", null);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this, "Google login failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    // ---------- Role handling & routing ----------

    private void routeUser(FirebaseUser user) {
        setLoading(true);
        db.collection("users").document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        openHomeForRole(snapshot);
                    } else {
                        askForRole(user);
                    }
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this, "Failed to load your profile, try again", Toast.LENGTH_LONG).show();
                });
    }

    private void askForRole(FirebaseUser user) {
        setLoading(false);
        final String[] roles = {"Manager", "Employee"};
        new AlertDialog.Builder(this)
                .setTitle("Welcome! What is your role?")
                .setCancelable(false)
                .setItems(roles, (dialog, which) -> {
                    String role = which == 0 ? "manager" : "employee";
                    createUserProfile(user, role);
                })
                .show();
    }

    private void createUserProfile(FirebaseUser user, String role) {
        setLoading(true);
        String name = user.getDisplayName();
        if (name == null || name.isEmpty()) {
            String email = user.getEmail() != null ? user.getEmail() : "user";
            name = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", user.getEmail());
        profile.put("role", role);
        profile.put("active", true);
        profile.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        db.collection("users").document(user.getUid())
                .set(profile)
                .addOnSuccessListener(unused -> {
                    analytics.logEvent("role_selected_" + role, null);
                    FirebaseCrashlytics.getInstance().log("User profile created with role " + role);
                    openHome(role);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this, "Failed to save your profile, try again", Toast.LENGTH_LONG).show();
                });
    }

    private void openHomeForRole(DocumentSnapshot userDoc) {
        String role = userDoc.getString("role");
        openHome(role != null ? role : "employee");
    }

    private void openHome(String role) {
        Intent intent;
        if ("manager".equals(role)) {
            intent = new Intent(this, MainActivity.class);
        } else {
            intent = new Intent(this, EmployeeActivity.class);
        }
        startActivity(intent);
        finish(); // don't keep the login screen behind the app
    }

    private void setLoading(boolean loading) {
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        btnRegister.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }
}
