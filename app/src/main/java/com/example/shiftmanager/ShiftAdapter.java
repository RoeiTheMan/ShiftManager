package com.example.shiftmanager;

import android.graphics.Color;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Fills the shift feed. The same adapter serves both roles:
 *
 *   manager  -> sees how many workers are approved and pending, button says "Manage"
 *   employee -> sees a "Sign up" / "Cancel" button that reflects whether they are
 *               already registered for that shift
 *
 * The employee's sign-up state is NOT kept in memory here. It is passed in from the
 * Activity as a map of shiftId -> registration status, which the Activity reads from
 * the shiftRegistrations collection. That is what makes a sign-up survive closing the
 * app, which the old in-memory boolean did not.
 */
public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ShiftViewHolder> {

    /** Lets the Activity react to taps without the adapter knowing about screens. */
    public interface OnShiftClickListener {
        /** The card itself was tapped -> open the shift's detail screen. */
        void onShiftClicked(Shift shift);

        /** The button on the card was tapped (Manage, or Sign up / Cancel). */
        void onShiftActionClicked(Shift shift);
    }

    private static final String COLOUR_PRIMARY = "#1976D2";
    private static final String COLOUR_CANCEL = "#B02F2F";
    private static final String COLOUR_DISABLED = "#9E9E9E";

    private final List<Shift> shiftList;
    private final boolean isManager;
    private final OnShiftClickListener listener;

    /** shiftId -> registration status ("pending" / "approved"). Employee feed only. */
    private final Map<String, String> registrationStatuses = new HashMap<>();

    /** Where the phone is right now, used to show "X km away". Null until we have it. */
    @Nullable
    private Location currentLocation;

    public ShiftAdapter(List<Shift> shiftList, boolean isManager, OnShiftClickListener listener) {
        this.shiftList = shiftList;
        this.isManager = isManager;
        this.listener = listener;
    }

    /** Called by the employee feed once it knows which shifts this user applied for. */
    public void setRegistrationStatuses(Map<String, String> statuses) {
        registrationStatuses.clear();
        if (statuses != null) {
            registrationStatuses.putAll(statuses);
        }
        notifyDataSetChanged();
    }

    /** Called once the GPS fix arrives, so the cards can show the distance. */
    public void setCurrentLocation(@Nullable Location location) {
        this.currentLocation = location;
        notifyDataSetChanged();
    }

    /** True when the signed-in employee already applied for this shift. */
    public boolean isRegisteredFor(String shiftId) {
        return registrationStatuses.containsKey(shiftId);
    }

    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.shift_item, parent, false);
        return new ShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder holder, int position) {
        final Shift shift = shiftList.get(position);

        holder.tvRole.setText(shift.getTitle());
        holder.tvDateTime.setText(buildWhenText(shift));
        holder.tvLocation.setText(buildWhereText(holder, shift));

        if (isManager) {
            bindManagerRow(holder, shift);
        } else {
            bindEmployeeRow(holder, shift);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShiftClicked(shift);
            }
        });

        holder.btnAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShiftActionClicked(shift);
            }
        });
    }

    /** "2026-08-12  |  09:00 - 17:00", skipping whichever part is missing. */
    private String buildWhenText(Shift shift) {
        String timeRange = shift.getTimeRange();
        if (shift.getDate().isEmpty()) {
            return timeRange;
        }
        if (timeRange.isEmpty()) {
            return shift.getDate();
        }
        return shift.getDate() + "  |  " + timeRange;
    }

    /** "Downtown Branch  ·  2.4 km away", with the distance only when we can work it out. */
    private String buildWhereText(ShiftViewHolder holder, Shift shift) {
        String distance = describeDistanceTo(holder, shift);
        if (shift.getLocation().isEmpty()) {
            return distance;
        }
        if (distance.isEmpty()) {
            return shift.getLocation();
        }
        return shift.getLocation() + "  ·  " + distance;
    }

    /**
     * How far the phone is from the shift, as text.
     *
     * Returns an empty string when we have no GPS fix yet, or when the shift was saved
     * without map coordinates -- in both cases there is simply nothing honest to show.
     */
    private String describeDistanceTo(ShiftViewHolder holder, Shift shift) {
        if (currentLocation == null || !shift.hasCoordinates()) {
            return "";
        }

        float[] result = new float[1];
        Location.distanceBetween(
                currentLocation.getLatitude(), currentLocation.getLongitude(),
                shift.getLatitude(), shift.getLongitude(),
                result);
        float metres = result[0];

        if (metres < 1000f) {
            return holder.itemView.getContext()
                    .getString(R.string.format_distance_m, Math.round(metres));
        }
        return holder.itemView.getContext()
                .getString(R.string.format_distance_km, metres / 1000f);
    }

    private void bindManagerRow(ShiftViewHolder holder, Shift shift) {
        String approved = holder.itemView.getContext().getString(
                R.string.format_approved, shift.getApprovedWorkers(), shift.getMaxWorkers());
        String pending = holder.itemView.getContext().getString(
                R.string.format_pending, shift.getPendingWorkers());

        holder.tvStatus.setText(shift.getPendingWorkers() > 0 ? approved + "  ·  " + pending : approved);
        holder.tvStatus.setVisibility(View.VISIBLE);

        holder.btnAction.setText(R.string.action_manage);
        holder.btnAction.setBackgroundColor(Color.parseColor(COLOUR_PRIMARY));
        holder.btnAction.setEnabled(true);
    }

    private void bindEmployeeRow(ShiftViewHolder holder, Shift shift) {
        holder.tvStatus.setVisibility(View.GONE);

        String myStatus = registrationStatuses.get(shift.getId());
        boolean alreadyRegistered = myStatus != null;

        if (alreadyRegistered) {
            // Already applied: the button becomes a way to withdraw.
            boolean approved = Constants.REGISTRATION_APPROVED.equals(myStatus);
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(approved
                    ? R.string.msg_approved
                    : R.string.format_pending_label);
            holder.btnAction.setText(R.string.action_cancel_registration);
            holder.btnAction.setBackgroundColor(Color.parseColor(COLOUR_CANCEL));
            holder.btnAction.setEnabled(true);
            return;
        }

        if (shift.isFull()) {
            // Nothing to apply for, so say why the button is dead instead of just greying it.
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setText(R.string.msg_shift_full);
            holder.btnAction.setText(R.string.action_sign_up);
            holder.btnAction.setBackgroundColor(Color.parseColor(COLOUR_DISABLED));
            holder.btnAction.setEnabled(false);
            return;
        }

        holder.btnAction.setText(R.string.action_sign_up);
        holder.btnAction.setBackgroundColor(Color.parseColor(COLOUR_PRIMARY));
        holder.btnAction.setEnabled(true);
    }

    @Override
    public int getItemCount() {
        return shiftList.size();
    }

    static class ShiftViewHolder extends RecyclerView.ViewHolder {
        final TextView tvRole;
        final TextView tvDateTime;
        final TextView tvLocation;
        final TextView tvStatus;
        final Button btnAction;

        ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
