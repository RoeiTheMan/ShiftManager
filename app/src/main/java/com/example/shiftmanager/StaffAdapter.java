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
 * The people on the Team screen.
 *
 * It draws memberships rather than users, because who somebody is and whether they are
 * actually in this business are two different facts. The same person can be a member of
 * one business and still waiting on another.
 */
public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

    public interface OnStaffActionListener {
        void onApproveMember(Membership membership);

        void onRemoveMember(Membership membership);
    }

    private final List<Membership> memberships;
    private final OnStaffActionListener listener;

    /** Only a manager gets the approve and remove buttons; everyone else just reads. */
    private final boolean canManage;

    public StaffAdapter(List<Membership> memberships, boolean canManage,
                        OnStaffActionListener listener) {
        this.memberships = memberships;
        this.canManage = canManage;
        this.listener = listener;
    }

    @NonNull
    @Override
    public StaffViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.staff_item, parent, false);
        return new StaffViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StaffViewHolder holder, int position) {
        final Membership membership = memberships.get(position);

        // The role is worth showing here: a team list that does not say who the managers
        // are is not much use for working out who can approve you.
        holder.tvName.setText(membership.getUserName() + " · " + membership.getUserRole());

        holder.tvEmail.setVisibility(membership.getUserEmail().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvEmail.setText(membership.getUserEmail());

        holder.tvStatus.setText(membership.getStatusLabel());
        holder.tvStatus.setTextColor(membership.isApproved() ? 0xFF2E7D32 : 0xFFB26A00);

        // Approve only appears on requests that are actually the manager's to answer.
        // Somebody the manager invited is waiting on that person, not on the manager, so
        // offering an Approve button there would be approving on their behalf.
        boolean managerCanApprove = canManage && membership.isWaitingOnManager();
        holder.btnApprove.setVisibility(managerCanApprove ? View.VISIBLE : View.GONE);
        holder.btnRemove.setVisibility(canManage ? View.VISIBLE : View.GONE);

        holder.btnApprove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onApproveMember(membership);
            }
        });

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveMember(membership);
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberships.size();
    }

    static class StaffViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvEmail;
        final TextView tvStatus;
        final Button btnApprove;
        final Button btnRemove;

        StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStaffName);
            tvEmail = itemView.findViewById(R.id.tvStaffEmail);
            tvStatus = itemView.findViewById(R.id.tvStaffStatus);
            btnApprove = itemView.findViewById(R.id.btnApproveStaff);
            btnRemove = itemView.findViewById(R.id.btnRemoveStaff);
        }
    }
}
