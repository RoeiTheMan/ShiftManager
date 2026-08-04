package com.example.shiftmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The list of people who applied for one shift, on the manager's approval screen.
 *
 * This is the screen HW2 calls "Shift Detail & Approval": it shows everyone who applied
 * and lets the manager approve a selection or remove staff who are already assigned.
 */
public class ApplicantAdapter extends RecyclerView.Adapter<ApplicantAdapter.ApplicantViewHolder> {

    /** How the screen hears about the manager's decisions. */
    public interface OnApplicantActionListener {
        void onApprove(Registration registration);

        void onRemove(Registration registration);
    }

    private final List<Registration> applicants;
    private final OnApplicantActionListener listener;

    public ApplicantAdapter(List<Registration> applicants, OnApplicantActionListener listener) {
        this.applicants = applicants;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ApplicantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.applicant_item, parent, false);
        return new ApplicantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ApplicantViewHolder holder, int position) {
        final Registration registration = applicants.get(position);

        // The role matters as much as the name here: the manager is filling specific
        // slots, so "Dana Levi · waiter" is what they need to decide on.
        holder.tvName.setText(registration.getNameWithRole());

        // Once somebody has checked in, when they arrived is more useful to the manager
        // than the approval status they already know about.
        if (registration.isCheckedIn()) {
            holder.tvStatus.setText(holder.itemView.getContext().getString(
                    R.string.format_manager_checked_in, registration.getCheckInTimeText()));
        } else {
            holder.tvStatus.setText(holder.itemView.getContext()
                    .getString(R.string.format_applicant_status, registration.getStatus()));
        }

        // Somebody already approved has nothing left to approve, so only Remove applies.
        holder.btnApprove.setVisibility(registration.isApproved() ? View.GONE : View.VISIBLE);

        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApprove(registration);
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemove(registration);
            }
        });
    }

    @Override
    public int getItemCount() {
        return applicants.size();
    }

    static class ApplicantViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvStatus;
        final Button btnApprove;
        final Button btnRemove;

        ApplicantViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvApplicantName);
            tvStatus = itemView.findViewById(R.id.tvApplicantStatus);
            btnApprove = itemView.findViewById(R.id.btnApprove);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}
