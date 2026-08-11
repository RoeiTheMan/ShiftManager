package com.example.shiftmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * The list of accounts a manager can add to their team.
 *
 * Kept separate from StaffAdapter because that one draws memberships -- people who are
 * already attached to a business. This draws accounts that are not, which is a different
 * question with no status to show.
 */
public class PeopleAdapter extends RecyclerView.Adapter<PeopleAdapter.PersonViewHolder> {

    public interface OnPersonPickedListener {
        void onPersonPicked(@NonNull AppUser person);
    }

    private final List<AppUser> people;
    private final OnPersonPickedListener listener;

    public PeopleAdapter(@NonNull List<AppUser> people,
                         @NonNull OnPersonPickedListener listener) {
        this.people = people;
        this.listener = listener;
    }

    @NonNull
    @Override
    public PersonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.person_item, parent, false);
        return new PersonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonViewHolder holder, int position) {
        final AppUser person = people.get(position);

        // The role matters when picking: adding a manager and adding an employee are the
        // same action here, and the manager should know which one they are inviting.
        holder.tvName.setText(person.getName() + " · " + person.getRole());

        holder.tvEmail.setVisibility(person.getEmail().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvEmail.setText(person.getEmail());

        holder.itemView.setOnClickListener(v -> listener.onPersonPicked(person));
    }

    @Override
    public int getItemCount() {
        return people.size();
    }

    static class PersonViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvEmail;

        PersonViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvPersonName);
            tvEmail = itemView.findViewById(R.id.tvPersonEmail);
        }
    }
}
