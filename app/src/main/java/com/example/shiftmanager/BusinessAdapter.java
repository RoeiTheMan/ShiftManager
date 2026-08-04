package com.example.shiftmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Draws the business list on the join screen and in the switcher.
 *
 * The same row serves the businesses the user already belongs to and the ones they are
 * searching through, because the only difference between the two is the status line on
 * the right -- and the row is told what that says rather than working it out.
 */
public class BusinessAdapter extends RecyclerView.Adapter<BusinessAdapter.BusinessViewHolder> {

    /** What the screen does when a row is tapped depends on the row's status. */
    public interface OnBusinessClickListener {
        void onBusinessClick(@NonNull BusinessRow row);
    }

    private final List<BusinessRow> rows;
    private final OnBusinessClickListener listener;

    public BusinessAdapter(@NonNull List<BusinessRow> rows,
                           @NonNull OnBusinessClickListener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @NonNull
    @Override
    public BusinessViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.business_item, parent, false);
        return new BusinessViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BusinessViewHolder holder, int position) {
        BusinessRow row = rows.get(position);
        Business business = row.getBusiness();
        View itemView = holder.itemView;

        holder.tvBusinessName.setText(business.getName());

        // Type and address are both optional, so hide the line rather than leaving a gap
        // or printing a stray separator.
        String subtitle = business.getSubtitle();
        if (subtitle.isEmpty()) {
            holder.tvBusinessSubtitle.setVisibility(View.GONE);
        } else {
            holder.tvBusinessSubtitle.setVisibility(View.VISIBLE);
            holder.tvBusinessSubtitle.setText(subtitle);
        }

        if (row.isMember()) {
            holder.tvBusinessStatus.setText(R.string.business_status_member);
            holder.tvBusinessStatus.setTextColor(0xFF2E7D32);
        } else if (row.isInvitation()) {
            holder.tvBusinessStatus.setText(R.string.business_status_invited);
            holder.tvBusinessStatus.setTextColor(0xFF1976D2);
        } else if (row.isAwaitingApproval()) {
            holder.tvBusinessStatus.setText(R.string.business_status_waiting);
            holder.tvBusinessStatus.setTextColor(0xFFB26A00);
        } else {
            holder.tvBusinessStatus.setText(R.string.business_status_join);
            holder.tvBusinessStatus.setTextColor(0xFF757575);
        }

        itemView.setOnClickListener(v -> listener.onBusinessClick(row));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class BusinessViewHolder extends RecyclerView.ViewHolder {

        final TextView tvBusinessName;
        final TextView tvBusinessSubtitle;
        final TextView tvBusinessStatus;

        BusinessViewHolder(@NonNull View itemView) {
            super(itemView);
            tvBusinessName = itemView.findViewById(R.id.tvBusinessName);
            tvBusinessSubtitle = itemView.findViewById(R.id.tvBusinessSubtitle);
            tvBusinessStatus = itemView.findViewById(R.id.tvBusinessStatus);
        }
    }
}
