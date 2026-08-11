package com.example.shiftmanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Draws the admin screen: business headings with their people underneath.
 *
 * Two row layouts, chosen by the row's own type, so one list can show both without
 * nesting a second RecyclerView inside each business.
 */
public class AdminAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnAdminRowListener {
        /** A business heading was tapped: open it up, or fold it away. */
        void onGroupToggled(@NonNull AdminRow row);

        /** A person was tapped: show what is actually knowable about their account. */
        void onPersonClicked(@NonNull AppUser person);
    }

    private final List<AdminRow> rows;
    private final OnAdminRowListener listener;

    public AdminAdapter(@NonNull List<AdminRow> rows, @NonNull OnAdminRowListener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == AdminRow.TYPE_GROUP) {
            return new GroupViewHolder(
                    inflater.inflate(R.layout.admin_group_item, parent, false));
        }
        return new PersonViewHolder(
                inflater.inflate(R.layout.admin_person_item, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final AdminRow row = rows.get(position);

        if (holder instanceof GroupViewHolder) {
            bindGroup((GroupViewHolder) holder, row);
        } else {
            bindPerson((PersonViewHolder) holder, row);
        }
    }

    private void bindGroup(@NonNull GroupViewHolder holder, @NonNull final AdminRow row) {
        holder.tvName.setText(row.getTitle());

        // Type and address are both optional on a business, and the orphan group has no
        // subtitle at all, so the line is hidden rather than left as an empty gap.
        if (row.getSubtitle().isEmpty()) {
            holder.tvSubtitle.setVisibility(View.GONE);
        } else {
            holder.tvSubtitle.setVisibility(View.VISIBLE);
            holder.tvSubtitle.setText(row.getSubtitle());
        }

        int count = row.getPeopleCount();
        holder.tvCount.setText(holder.itemView.getResources()
                .getQuantityString(R.plurals.admin_people_count, count, count));

        // A caret rather than an icon file: it says which way the group is facing without
        // adding a drawable, and it reads the same on every screen density.
        holder.tvChevron.setText(row.isExpanded() ? "▲" : "▼");

        holder.itemView.setOnClickListener(v -> listener.onGroupToggled(row));
    }

    private void bindPerson(@NonNull PersonViewHolder holder, @NonNull final AdminRow row) {
        final AppUser person = row.getPerson();
        if (person == null) {
            return;
        }

        // Duplicate profiles for one email are indistinguishable by name and role -- that
        // is exactly the mess this screen has to help clear up. Two things tell them
        // apart: which one is the live profile behind the current login, and when each
        // was made. The live one is marked, and it is the only one that cannot be deleted.
        boolean isSignedInUser = Session.getUser() != null
                && person.getId().equals(Session.getUser().getId());

        String name = person.getName() + " · " + person.getRole();
        holder.tvName.setText(isSignedInUser
                ? holder.itemView.getContext().getString(R.string.format_person_you, name)
                : name);

        holder.tvEmail.setVisibility(person.getEmail().isEmpty() ? View.GONE : View.VISIBLE);
        holder.tvEmail.setText(holder.itemView.getContext().getString(
                R.string.format_person_email_created,
                person.getEmail(), person.getCreatedLabel()));

        holder.tvStatus.setText(row.getStatusLabel());
        // Green once they are actually in the business, amber while anyone is still
        // waiting on an answer -- the same colours the Team screen uses, so a status
        // means the same thing wherever it is read.
        holder.tvStatus.setTextColor(
                "Member".equals(row.getStatusLabel()) ? 0xFF2E7D32 : 0xFFB26A00);

        holder.itemView.setOnClickListener(v -> listener.onPersonClicked(person));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvSubtitle;
        final TextView tvCount;
        final TextView tvChevron;

        GroupViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvAdminGroupName);
            tvSubtitle = itemView.findViewById(R.id.tvAdminGroupSubtitle);
            tvCount = itemView.findViewById(R.id.tvAdminGroupCount);
            tvChevron = itemView.findViewById(R.id.tvAdminGroupChevron);
        }
    }

    static class PersonViewHolder extends RecyclerView.ViewHolder {
        final TextView tvName;
        final TextView tvEmail;
        final TextView tvStatus;

        PersonViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvAdminPersonName);
            tvEmail = itemView.findViewById(R.id.tvAdminPersonEmail);
            tvStatus = itemView.findViewById(R.id.tvAdminPersonStatus);
        }
    }
}
