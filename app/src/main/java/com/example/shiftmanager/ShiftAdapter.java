package com.example.shiftmanager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ShiftAdapter extends RecyclerView.Adapter<ShiftAdapter.ShiftViewHolder> {

    private List<Shift> shiftList;
    private boolean isManager;

    public ShiftAdapter(List<Shift> shiftList, boolean isManager) {
        this.shiftList = shiftList;
        this.isManager = isManager;
    }

    @NonNull
    @Override
    public ShiftViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.shift_item, parent, false);
        return new ShiftViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShiftViewHolder holder, int position) {
        Shift shift = shiftList.get(position);
        holder.tvRole.setText(shift.getTitle());
        holder.tvDateTime.setText(shift.getDate() + " | " + shift.getTime());

        if (isManager) {
            holder.tvStatus.setText("Approved: " + shift.getApprovedWorkers() + "/" + shift.getMaxWorkers() + " | Pending: " + shift.getPendingWorkers());
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.btnAction.setText("Manage");
            holder.btnAction.setBackgroundColor(Color.parseColor("#1976D2"));
            holder.btnAction.setEnabled(true);
        } else {
            holder.tvStatus.setVisibility(View.GONE);

            if (shift.isSignedUp()) {
                holder.btnAction.setText("Cancel"); // Let them know they can unsign
                holder.btnAction.setBackgroundColor(Color.GRAY);
                holder.btnAction.setEnabled(true); // WE UNLOCKED IT!
            } else {
                holder.btnAction.setText("Sign Up");
                holder.btnAction.setBackgroundColor(Color.parseColor("#1976D2"));
                holder.btnAction.setEnabled(true);
            }
        }

        holder.btnAction.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isManager) {
                    android.widget.Toast.makeText(v.getContext(), "Managing: " + shift.getTitle(), android.widget.Toast.LENGTH_SHORT).show();
                } else {
                    // The new Unsign Toggle!
                    if (shift.isSignedUp()) {
                        shift.setSignedUp(false);
                        notifyItemChanged(holder.getAdapterPosition());
                        android.widget.Toast.makeText(v.getContext(), "Canceled shift: " + shift.getTitle(), android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        shift.setSignedUp(true);
                        notifyItemChanged(holder.getAdapterPosition());
                        android.widget.Toast.makeText(v.getContext(), "Signed up for: " + shift.getTitle(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return shiftList.size();
    }

    public static class ShiftViewHolder extends RecyclerView.ViewHolder {
        TextView tvRole, tvDateTime, tvStatus;
        Button btnAction;

        public ShiftViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRole = itemView.findViewById(R.id.tvRole);
            tvDateTime = itemView.findViewById(R.id.tvDateTime);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}