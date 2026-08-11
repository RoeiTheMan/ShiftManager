package com.example.shiftmanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a signed-in person gets attached to a business -- join flows 1, 2 and 3, plus the
 * employee's side of flow 4.
 *
 * One list does the whole job. With the search box empty it shows the businesses the user
 * already has some link to, so they can walk back into one or answer an invitation. As
 * soon as they type, it becomes a search over every business in the app and the list
 * narrows, which is how somebody finds a business they are not in yet.
 *
 * Everything that is not already approved goes through a request. Nobody is ever put into
 * a business without the other side agreeing.
 */
public class BusinessSetupActivity extends AppCompatActivity
        implements BusinessAdapter.OnBusinessClickListener {

    private final BusinessRepository businessRepository = new BusinessRepository();
    private final MembershipRepository membershipRepository = new MembershipRepository();
    private final UserRepository userRepository = new UserRepository();

    private EditText etSearchBusiness;
    private Button btnCreateBusiness, btnLogoutSetup;
    private RecyclerView rvBusinesses;
    private ProgressBar progressSetup;
    private TextView tvSetupSubtitle, tvEmptyBusinesses;

    private final List<BusinessRow> rows = new ArrayList<>();
    private BusinessAdapter adapter;

    /** This user's links, keyed by business id, so a search result knows its own status. */
    private final Map<String, Membership> myMemberships = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_setup);

        if (!Session.hasUser()) {
            goToLogin();
            return;
        }

        etSearchBusiness = findViewById(R.id.etSearchBusiness);
        btnCreateBusiness = findViewById(R.id.btnCreateBusiness);
        btnLogoutSetup = findViewById(R.id.btnLogoutSetup);
        rvBusinesses = findViewById(R.id.rvBusinesses);
        progressSetup = findViewById(R.id.progressSetup);
        tvSetupSubtitle = findViewById(R.id.tvSetupSubtitle);
        tvEmptyBusinesses = findViewById(R.id.tvEmptyBusinesses);

        adapter = new BusinessAdapter(rows, this);
        rvBusinesses.setLayoutManager(new LinearLayoutManager(this));
        rvBusinesses.setAdapter(adapter);

        // Only managers create businesses. An employee joins one that already exists.
        AppUser user = Session.getUser();
        boolean canCreate = user.isManager() || user.isAdmin();
        btnCreateBusiness.setVisibility(canCreate ? View.VISIBLE : View.GONE);
        tvSetupSubtitle.setText(canCreate
                ? R.string.business_setup_subtitle_manager
                : R.string.business_setup_subtitle_employee);

        btnCreateBusiness.setOnClickListener(v ->
                startActivity(new Intent(this, CreateBusinessActivity.class)));

        // Signing out has to be reachable from here. Somebody who registered as the wrong
        // role would otherwise be stuck on this screen with no way back.
        btnLogoutSetup.setOnClickListener(v -> logout());

        etSearchBusiness.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Empty means "show everything", not "show nothing".
                searchBusinesses(s.toString().trim());
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Reloaded every time the screen comes back, so a request approved elsewhere shows
        // up without the user having to sign out and in again.
        refreshMemberships();
    }

    // ---------- Loading ----------

    /** Reads this user's links first; every list on this screen is drawn against them. */
    private void refreshMemberships() {
        setLoading(true);
        membershipRepository.loadMembershipsForUser(Session.getUser().getId(),
                new Callback<List<Membership>>() {
                    @Override
                    public void onSuccess(List<Membership> memberships) {
                        myMemberships.clear();
                        for (Membership membership : memberships) {
                            myMemberships.put(membership.getBusinessId(), membership);
                        }

                        // An empty box lists every business rather than nothing, so
                        // searchBusinesses is called either way.
                        searchBusinesses(etSearchBusiness.getText().toString().trim());
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        Toast.makeText(BusinessSetupActivity.this,
                                R.string.msg_load_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Lists businesses: everything when the box is empty, narrowing as the user types.
     *
     * Showing the full list up front is deliberate. Somebody opening this app has no way
     * to know what businesses exist, so an empty-until-you-type box asks them to guess a
     * name -- which is a dead end for anyone who is not already in a team, including a
     * lecturer marking the project. Roei raised exactly that on 2026-08-11.
     *
     * Businesses the user already has a link to keep their real status, so the list can
     * mix "Member", "Waiting" and "Tap to join" rows without offering to join something
     * twice.
     */
    private void searchBusinesses(@NonNull String query) {
        setLoading(true);
        businessRepository.searchBusinesses(query, new Callback<List<Business>>() {
            @Override
            public void onSuccess(List<Business> businesses) {
                rows.clear();
                for (Business business : businesses) {
                    // A search hit the user is already linked to keeps its real status
                    // rather than offering "request to join" a second time.
                    rows.add(new BusinessRow(business, myMemberships.get(business.getId())));
                }
                adapter.notifyDataSetChanged();
                setLoading(false);
                // An empty list means two different things now: nothing matched what was
                // typed, or the app genuinely has no businesses in it yet.
                showEmptyIfNeeded(query.isEmpty()
                        ? R.string.empty_no_businesses_at_all
                        : R.string.empty_no_search_results);
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                Toast.makeText(BusinessSetupActivity.this,
                        R.string.msg_load_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Tapping a row ----------

    @Override
    public void onBusinessClick(@NonNull BusinessRow row) {
        if (row.isMember()) {
            enterBusiness(row.getBusiness());
            return;
        }
        if (row.isInvitation()) {
            confirmInvitation(row);
            return;
        }
        if (row.isAwaitingApproval()) {
            Toast.makeText(this, R.string.msg_still_waiting, Toast.LENGTH_LONG).show();
            return;
        }
        confirmJoinRequest(row.getBusiness());
    }

    /** Flow 3, and flow 2 for a manager: ask to join, a manager of that business answers. */
    private void confirmJoinRequest(@NonNull final Business business) {
        new AlertDialog.Builder(this)
                .setTitle(business.getName())
                .setMessage(getString(R.string.confirm_request_join, business.getName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_request_join, (dialog, which) ->
                        sendJoinRequest(business))
                .show();
    }

    private void sendJoinRequest(@NonNull final Business business) {
        setLoading(true);
        membershipRepository.createMembership(business.getId(), Session.getUser(),
                Constants.REQUESTED_BY_JOINER, Constants.MEMBERSHIP_PENDING,
                new Callback<String>() {
                    @Override
                    public void onSuccess(String membershipId) {
                        Toast.makeText(BusinessSetupActivity.this,
                                R.string.msg_join_requested, Toast.LENGTH_LONG).show();
                        etSearchBusiness.setText("");
                        refreshMemberships();
                    }

                    @Override
                    public void onError(Exception e) {
                        setLoading(false);
                        Toast.makeText(BusinessSetupActivity.this,
                                R.string.msg_save_failed, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /** Flow 4 from the employee's side: a manager added them, and they answer. */
    private void confirmInvitation(@NonNull final BusinessRow row) {
        final Membership membership = row.getMembership();
        new AlertDialog.Builder(this)
                .setTitle(row.getBusiness().getName())
                .setMessage(getString(R.string.confirm_accept_invite,
                        row.getBusiness().getName()))
                .setNegativeButton(R.string.action_decline, (dialog, which) ->
                        answerInvitation(membership, Constants.MEMBERSHIP_REJECTED, null))
                .setPositiveButton(R.string.action_accept, (dialog, which) ->
                        answerInvitation(membership, Constants.MEMBERSHIP_APPROVED,
                                row.getBusiness()))
                .show();
    }

    private void answerInvitation(@NonNull Membership membership, @NonNull String status,
                                  final Business businessToOpen) {
        setLoading(true);
        membershipRepository.setStatus(membership.getId(), status, new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                if (businessToOpen != null) {
                    enterBusiness(businessToOpen);
                } else {
                    Toast.makeText(BusinessSetupActivity.this,
                            R.string.msg_invite_declined, Toast.LENGTH_SHORT).show();
                    refreshMemberships();
                }
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                Toast.makeText(BusinessSetupActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Entering a business ----------

    /**
     * Makes this the business the user is working in, then opens their home screen.
     *
     * The choice is saved on the user document rather than just held in memory, so
     * somebody who belongs to three businesses does not have to re-pick on every launch.
     */
    private void enterBusiness(@NonNull final Business business) {
        final AppUser user = Session.getUser();
        setLoading(true);

        userRepository.setActiveBusiness(user.getId(), business.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void unused) {
                Session.setUser(new AppUser(user.getId(), user.getName(), user.getEmail(),
                        user.getRole(), user.isActive(), business.getId()));
                Session.setBusiness(business);

                // The admin does not normally reach this screen at all -- they are routed
                // to their own overview at sign-in -- but if they ever join a business
                // they work in it as a manager would.
                Intent intent = new Intent(BusinessSetupActivity.this,
                        user.isManager() || user.isAdmin()
                                ? MainActivity.class
                                : EmployeeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(Exception e) {
                setLoading(false);
                Toast.makeText(BusinessSetupActivity.this,
                        R.string.msg_save_failed, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------- Plumbing ----------

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        goToLogin();
    }

    private void goToLogin() {
        Session.clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showEmptyIfNeeded(int messageRes) {
        if (rows.isEmpty()) {
            showEmpty(messageRes);
        } else {
            tvEmptyBusinesses.setVisibility(View.GONE);
        }
    }

    private void showEmpty(int messageRes) {
        tvEmptyBusinesses.setText(messageRes);
        tvEmptyBusinesses.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        progressSetup.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            tvEmptyBusinesses.setVisibility(View.GONE);
        }
    }
}
