package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

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
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private static final int RC_GOOGLE_SIGN_IN = 9001;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseAnalytics analytics;
    private GoogleSignInClient googleSignInClient;

    private final MembershipRepository membershipRepository = new MembershipRepository();
    private final BusinessRepository businessRepository = new BusinessRepository();

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

    // ---------- Routing ----------

    /**
     * Decides where a signed-in person lands, in three steps.
     *
     * No profile at all means a brand new account, which goes to the sign-up screens.
     * A profile with no approved business means they registered but have not been let
     * into a team yet, so they go back to the business screen. Only somebody who is both
     * a known person and an approved member of a business reaches a shift feed.
     *
     * Getting this order right is the whole point of the rewrite: the app used to drop a
     * new account straight onto an empty dashboard belonging to nothing.
     */
    private void routeUser(FirebaseUser user) {
        setLoading(true);
        db.collection(Constants.COLLECTION_USERS).document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        openProfileSetup();
                        return;
                    }
                    AppUser appUser = AppUser.fromDocument(snapshot);
                    Session.setUser(appUser);
                    resolveBusiness(appUser);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    FirebaseCrashlytics.getInstance().recordException(e);
                    Toast.makeText(LoginActivity.this,
                            "Failed to load your profile, try again", Toast.LENGTH_LONG).show();
                });
    }

    /**
     * Works out which business to open, from the ones this person has actually been
     * approved for.
     *
     * The stored choice is checked against that list rather than trusted, because a
     * manager can remove somebody after they last used the app -- and reopening a business
     * they are no longer in would show them a team they have been taken out of.
     */
    private void resolveBusiness(final AppUser appUser) {
        membershipRepository.loadMembershipsForUser(appUser.getId(),
                new Callback<java.util.List<Membership>>() {
                    @Override
                    public void onSuccess(java.util.List<Membership> memberships) {
                        java.util.List<String> approvedIds = new java.util.ArrayList<>();
                        for (Membership membership : memberships) {
                            if (membership.isApproved()) {
                                approvedIds.add(membership.getBusinessId());
                            }
                        }

                        if (approvedIds.isEmpty()) {
                            openBusinessSetup();
                            return;
                        }

                        String target = appUser.getActiveBusinessId();
                        if (target.isEmpty() || !approvedIds.contains(target)) {
                            target = approvedIds.get(0);
                        }
                        openBusiness(appUser, target);
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        FirebaseCrashlytics.getInstance().recordException(e);
                        Toast.makeText(LoginActivity.this,
                                R.string.msg_load_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void openBusiness(final AppUser appUser, final String businessId) {
        businessRepository.loadBusiness(businessId, new Callback<Business>() {
            @Override
            public void onSuccess(Business business) {
                Session.setBusiness(business);
                openHome(appUser);
            }

            @Override
            public void onError(Exception e) {
                // The business was deleted out from under them. Sending them to the
                // business screen is better than a dead dashboard with no data.
                setLoading(false);
                openBusinessSetup();
            }
        });
    }

    private void openProfileSetup() {
        startActivity(new Intent(this, ProfileSetupActivity.class));
        finish();
    }

    private void openBusinessSetup() {
        startActivity(new Intent(this, BusinessSetupActivity.class));
        finish();
    }

    private void openHome(AppUser appUser) {
        analytics.logEvent("home_opened_" + appUser.getRole(), null);

        Intent intent = new Intent(this,
                appUser.isManager() || appUser.isAdmin()
                        ? MainActivity.class
                        : EmployeeActivity.class);
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
