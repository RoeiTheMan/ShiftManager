package com.example.shiftmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** The employee list shown in the Staff Directory. */
public class StaffAdapter extends RecyclerView.Adapter<StaffAdapter.StaffViewHolder> {

    public interface OnStaffActionListener {
        void onRemoveEmployee(AppUser employee);
    }

    private final List<AppUser> employees;
    private final OnStaffActionListener listener;

    public StaffAdapter(List<AppUser> employees, OnStaffActionListener listener) {
        this.employees = employees;
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
        final AppUser employee = employees.get(position);

        holder.tvName.setText(employee.getName());

        // Somebody added by hand may have no email yet; hiding the row beats a blank line.
        holder.tvEmail.setVisibility(employee.getEmail().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvEmail.setText(employee.getEmail());

        holder.btnRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemoveEmployee(employee);
            }
        });
    }

    @Override
    public int getItemCount() {
        return employees.size();
    }

    static class StaffViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvEmail;
        final Button btnRemove;

        StaffViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvStaffName);
            tvEmail = itemView.findViewById(R.id.tvStaffEmail);
            btnRemove = itemView.findViewById(R.id.btnRemoveStaff);
        }
    }
}
