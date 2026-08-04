package com.example.shiftmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One line on the admin screen, which is either a group heading or a person inside it.
 *
 * The screen shows businesses that open up to reveal their people, so the list is not a
 * plain list of one kind of thing. Rather than nesting a RecyclerView inside another one,
 * the whole thing is flattened into a single list of rows and the adapter draws each row
 * according to its type. Collapsing a business simply means its person rows are left out
 * when the list is rebuilt.
 *
 * The accounts that belong to no business at all are shown as one more group, with an
 * empty group id. That group behaves exactly like a business -- it just has no business
 * document behind it -- which keeps expanding and collapsing to a single code path.
 */
public class AdminRow {

    public static final int TYPE_GROUP = 0;
    public static final int TYPE_PERSON = 1;

    private final int type;

    // ---------- Group rows ----------

    private final String groupId;
    private final String title;
    private final String subtitle;
    private final int peopleCount;
    private final boolean expanded;

    // ---------- Person rows ----------

    private final AppUser person;
    private final String statusLabel;

    private AdminRow(int type, String groupId, String title, String subtitle,
                     int peopleCount, boolean expanded,
                     AppUser person, String statusLabel) {
        this.type = type;
        this.groupId = groupId;
        this.title = title;
        this.subtitle = subtitle;
        this.peopleCount = peopleCount;
        this.expanded = expanded;
        this.person = person;
        this.statusLabel = statusLabel;
    }

    /** A business heading, or the heading of the "not in any business" group. */
    @NonNull
    public static AdminRow group(@NonNull String groupId, @NonNull String title,
                                 @NonNull String subtitle, int peopleCount,
                                 boolean expanded) {
        return new AdminRow(TYPE_GROUP, groupId, title, subtitle, peopleCount, expanded,
                null, "");
    }

    /** One person under a heading. The status is their standing in THAT business. */
    @NonNull
    public static AdminRow person(@NonNull AppUser person, @NonNull String statusLabel) {
        return new AdminRow(TYPE_PERSON, "", person.getName(), person.getEmail(), 0, false,
                person, statusLabel);
    }

    public int getType() { return type; }
    public String getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public int getPeopleCount() { return peopleCount; }
    public boolean isExpanded() { return expanded; }
    public String getStatusLabel() { return statusLabel; }

    @Nullable
    public AppUser getPerson() { return person; }
}
