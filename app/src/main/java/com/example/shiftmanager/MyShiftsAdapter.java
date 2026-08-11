package com.example.shiftmanager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The employee's own shifts.
 *
 * Each row is a registration rather than a shift, because the thing the employee wants to
 * know is not only when the shift is but whether the manager has approved them yet.
 */
public class MyShiftsAdapter extends RecyclerView.Adapter<MyShiftsAdapter.MyShiftViewHolder> {

    /** Lets the screen do the GPS work; the adapter only knows a button was pressed. */
    public interface OnCheckInListener {
        void onCheckIn(Registration registration);

        /**
         * The row itself was tapped, to read the whole shift.
         *
         * Added after Roei found the card was a dead end on 2026-08-11: it names a shift
         * but there was no way to open it, so an employee could not see the description,
         * the site, or who else was on it.
         */
        void onShiftOpened(Registration registration);
    }

    private static final String COLOUR_APPROVED = "#2E7D32";
    private static final String COLOUR_PENDING = "#EF6C00";

    private final List<Registration> registrations;
    private final OnCheckInListener listener;

    public MyShiftsAdapter(List<Registration> registrations, OnCheckInListener listener) {
        this.registrations = registrations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MyShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.my_shift_item, parent, false);
        return new MyShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyShiftViewHolder holder, int position) {
        Registration registration = registrations.get(position);
        Shift shift = registration.getShift();

        if (shift == null) {
            // The repository drops registrations whose shift is gone, so this should not
            // happen -- but showing the row blank beats crashing if it ever does.
            holder.tvTitle.setText(R.string.shift_title_placeholder);
            holder.tvWhen.setText("");
            holder.tvWhere.setText("");
            return;
        }

        holder.tvTitle.setText(shift.getTitle());

        String when = shift.getDate();
        if (!shift.getTimeRange().isEmpty()) {
            when = when + "  |  " + shift.getTimeRange();
        }
        holder.tvWhen.setText(when);

        holder.tvWhere.setVisibility(shift.getLocation().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvWhere.setText(shift.getLocation());

        if (registration.isApproved()) {
            holder.tvStatus.setText(R.string.msg_approved);
            holder.tvStatus.setTextColor(Color.parseColor(COLOUR_APPROVED));
        } else {
            holder.tvStatus.setText(R.string.format_pending_label);
            holder.tvStatus.setTextColor(Color.parseColor(COLOUR_PENDING));
        }

        bindCheckIn(holder, registration);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShiftOpened(registration);
            }
        });
    }

    /**
     * Shows the check-in button, the recorded check-in, or neither.
     *
     * Only an approved employee sees the button: somebody still waiting on the manager has
     * not been given the shift, so there is nothing for them to arrive at yet.
     */
    private void bindCheckIn(MyShiftViewHolder holder, Registration registration) {
        if (registration.isCheckedIn()) {
            holder.btnCheckIn.setVisibility(View.GONE);
            holder.tvCheckedIn.setVisibility(View.VISIBLE);

            int distance = registration.getCheckInDistanceMetres();
            // Distance is -1 when the shift had no saved position to compare against, so
            // there is nothing honest to report about how close they were.
            holder.tvCheckedIn.setText(distance >= 0
                    ? holder.itemView.getContext().getString(
                            R.string.format_checked_in_distance,
                            registration.getCheckInTimeText(), distance)
                    : holder.itemView.getContext().getString(
                            R.string.format_checked_in, registration.getCheckInTimeText()));
            return;
        }

        holder.tvCheckedIn.setVisibility(View.GONE);
        holder.btnCheckIn.setVisibility(registration.canCheckIn() ? View.VISIBLE : View.GONE);
        holder.btnCheckIn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCheckIn(registration);
            }
        });
    }

    @Override
    public int getItemCount() {
        return registrations.size();
    }

    static class MyShiftViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTitle;
        final TextView tvWhen;
        final TextView tvWhere;
        final TextView tvStatus;
        final TextView tvCheckedIn;
        final android.widget.Button btnCheckIn;

        MyShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvMyShiftTitle);
            tvWhen = itemView.findViewById(R.id.tvMyShiftWhen);
            tvWhere = itemView.findViewById(R.id.tvMyShiftWhere);
            tvStatus = itemView.findViewById(R.id.tvMyShiftStatus);
            tvCheckedIn = itemView.findViewById(R.id.tvCheckedIn);
            btnCheckIn = itemView.findViewById(R.id.btnCheckIn);
        }
    }
}
